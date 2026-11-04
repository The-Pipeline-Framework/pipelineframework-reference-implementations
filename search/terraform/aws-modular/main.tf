provider "aws" {
  region = var.aws_region
}

locals {
  base_environment = {
    QUARKUS_PROFILE = "lambda-modular"
    # This manual example leans on CloudWatch/Lambda metrics instead of in-process exporters.
    QUARKUS_OTEL_ENABLED                         = "false"
    QUARKUS_MICROMETER_EXPORT_PROMETHEUS_ENABLED = "false"
  }

  step_functions = {
    crawl-source-svc = {
      zip_path = "${path.root}/${var.crawl_source_zip_path}"
      timeout  = 60
    }
    parse-document-svc = {
      zip_path = "${path.root}/${var.parse_document_zip_path}"
      timeout  = 60
    }
    tokenize-content-svc = {
      zip_path = "${path.root}/${var.tokenize_content_zip_path}"
      timeout  = 60
    }
    embed-content-svc = {
      zip_path = "${path.root}/${var.embed_content_zip_path}"
      timeout  = 60
    }
    index-document-svc = {
      zip_path = "${path.root}/${var.index_document_zip_path}"
      timeout  = 60
    }
  }

  queue_environment = merge(local.base_environment, {
    PIPELINE_CACHE_PROVIDER                                     = "memory"
    PIPELINE_ORCHESTRATOR_MODE                                  = "QUEUE_ASYNC"
    PIPELINE_ORCHESTRATOR_RELEASE_VERSION                       = local.release_version
    PIPELINE_ORCHESTRATOR_RELEASES_REGISTRY_PROVIDER            = "dynamo"
    PIPELINE_ORCHESTRATOR_DYNAMO_RELEASE_TABLE                  = aws_dynamodb_table.queue["release"].name
    PIPELINE_ORCHESTRATOR_IDEMPOTENCY_POLICY                    = "CLIENT_KEY_REQUIRED"
    PIPELINE_ORCHESTRATOR_PROCESS_LOOPS_DISABLED                = "true"
    PIPELINE_ORCHESTRATOR_SQS_LOCAL_LOOPBACK                    = "false"
    PIPELINE_ORCHESTRATOR_LEASE_MS                              = "180000"
    PIPELINE_ORCHESTRATOR_STATE_PROVIDER                        = "dynamo"
    PIPELINE_ORCHESTRATOR_DISPATCHER_PROVIDER                   = "sqs"
    PIPELINE_ORCHESTRATOR_QUEUE_URL                             = aws_sqs_queue.work.url
    PIPELINE_ORCHESTRATOR_DYNAMO_EXECUTION_TABLE                = aws_dynamodb_table.queue["execution"].name
    PIPELINE_ORCHESTRATOR_DYNAMO_EXECUTION_KEY_TABLE            = aws_dynamodb_table.queue["execution-key"].name
    PIPELINE_ORCHESTRATOR_DYNAMO_EXECUTION_PAYLOAD_TABLE        = aws_dynamodb_table.queue["execution-payload"].name
    PIPELINE_COMMAND_EFFECT_STORE_PROVIDER                      = "dynamo"
    PIPELINE_COMMAND_EFFECT_STORE_DYNAMO_TABLE                  = aws_dynamodb_table.queue["command-effect"].name
    QUARKUS_REST_CLIENT_PROCESS_CRAWL_SOURCE_URL                = aws_lambda_function_url.step["crawl-source-svc"].function_url
    QUARKUS_REST_CLIENT_PROCESS_PARSE_DOCUMENT_URL              = aws_lambda_function_url.step["parse-document-svc"].function_url
    QUARKUS_REST_CLIENT_PROCESS_TOKENIZE_CONTENT_URL            = aws_lambda_function_url.step["tokenize-content-svc"].function_url
    QUARKUS_REST_CLIENT_PROCESS_EMBED_CONTENT_URL               = aws_lambda_function_url.step["embed-content-svc"].function_url
    QUARKUS_REST_CLIENT_PROCESS_BUILD_SEARCH_INDEX_DOCUMENT_URL = aws_lambda_function_url.step["index-document-svc"].function_url
    QUARKUS_REST_CLIENT_PROCESS_SUMMARIZE_INDEX_WRITES_URL      = aws_lambda_function_url.step["index-document-svc"].function_url
  })

  release_version = "sha256:${sha256(jsonencode({ for name, package in local.lambda_packages : name => filesha256(package.zip_path) }))}"

  orchestrator_zip_path = "${path.root}/${var.orchestrator_zip_path}"

  lambda_packages = merge(local.step_functions, {
    orchestrator-svc = { zip_path = local.orchestrator_zip_path }
    function-worker  = { zip_path = "${path.root}/${var.worker_zip_path}" }
  })
}

# Lambda's inline API upload is limited to 50 MiB compressed. Stage the unchanged
# packages in a private bucket in this deployment's region; destroy removes them.
resource "aws_s3_bucket" "lambda_code" {
  bucket_prefix = "${var.name_prefix}-code-"
  force_destroy = true
}

resource "aws_s3_bucket_public_access_block" "lambda_code" {
  bucket = aws_s3_bucket.lambda_code.id

  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_server_side_encryption_configuration" "lambda_code" {
  bucket = aws_s3_bucket.lambda_code.id

  rule {
    apply_server_side_encryption_by_default {
      sse_algorithm = "AES256"
    }
  }
}

resource "aws_s3_object" "lambda_code" {
  for_each = local.lambda_packages

  bucket      = aws_s3_bucket.lambda_code.id
  key         = "${each.key}/${filesha256(each.value.zip_path)}/function.zip"
  source      = each.value.zip_path
  source_hash = filesha256(each.value.zip_path)

  depends_on = [
    aws_s3_bucket_public_access_block.lambda_code,
    aws_s3_bucket_server_side_encryption_configuration.lambda_code,
  ]
}

data "aws_iam_policy_document" "lambda_assume_role" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["lambda.amazonaws.com"]
    }
  }
}

resource "aws_iam_role" "lambda_exec" {
  name               = "${var.name_prefix}-lambda-exec"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role_policy_attachment" "basic_execution" {
  role       = aws_iam_role.lambda_exec.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_lambda_function" "step" {
  for_each = local.step_functions

  function_name    = "${var.name_prefix}-${each.key}"
  role             = aws_iam_role.lambda_exec.arn
  s3_bucket        = aws_s3_object.lambda_code[each.key].bucket
  s3_key           = aws_s3_object.lambda_code[each.key].key
  source_code_hash = filebase64sha256(each.value.zip_path)
  runtime          = "java21"
  handler          = "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"
  architectures    = ["x86_64"]
  memory_size      = 1024
  timeout          = each.value.timeout

  environment {
    variables = local.base_environment
  }

  depends_on = [aws_iam_role_policy_attachment.basic_execution]
}

resource "aws_lambda_function_url" "step" {
  for_each = aws_lambda_function.step

  function_name = each.value.function_name
  # Public Function URLs are intentional for the disposable manual example workflow.
  authorization_type = "NONE"
  invoke_mode        = "BUFFERED"
}

resource "aws_lambda_permission" "step_function_url_invoke" {
  for_each = aws_lambda_function.step

  # Keep the explicit invoke permission for the public Function URL example path.
  statement_id             = "AllowPublicFunctionInvoke"
  action                   = "lambda:InvokeFunction"
  function_name            = each.value.function_name
  principal                = "*"
  invoked_via_function_url = true

  depends_on = [aws_lambda_function_url.step]
}

resource "aws_lambda_permission" "step_function_url_public_access" {
  for_each = aws_lambda_function.step

  statement_id           = "AllowPublicFunctionUrlInvoke"
  action                 = "lambda:InvokeFunctionUrl"
  function_name          = each.value.function_name
  principal              = "*"
  function_url_auth_type = "NONE"

  depends_on = [aws_lambda_permission.step_function_url_invoke]
}

resource "aws_lambda_function" "orchestrator" {
  function_name    = "${var.name_prefix}-orchestrator-svc"
  role             = aws_iam_role.queue_exec.arn
  s3_bucket        = aws_s3_object.lambda_code["orchestrator-svc"].bucket
  s3_key           = aws_s3_object.lambda_code["orchestrator-svc"].key
  source_code_hash = filebase64sha256(local.orchestrator_zip_path)
  runtime          = "java21"
  handler          = "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"
  architectures    = ["x86_64"]
  memory_size      = 1536
  timeout          = 120

  environment {
    variables = local.queue_environment
  }

  depends_on = [aws_iam_role_policy_attachment.queue_logs, aws_iam_role_policy.queue_access]
}

resource "aws_lambda_function_url" "orchestrator" {
  function_name = aws_lambda_function.orchestrator.function_name
  # Public Function URLs are intentional for the disposable manual example workflow.
  authorization_type = "NONE"
  invoke_mode        = "BUFFERED"
}

resource "aws_lambda_permission" "orchestrator_function_url_invoke" {
  # Keep the explicit invoke permission for the public Function URL example path.
  statement_id             = "AllowPublicFunctionInvoke"
  action                   = "lambda:InvokeFunction"
  function_name            = aws_lambda_function.orchestrator.function_name
  principal                = "*"
  invoked_via_function_url = true

  depends_on = [aws_lambda_function_url.orchestrator]
}

resource "aws_lambda_permission" "orchestrator_function_url_public_access" {
  statement_id           = "AllowPublicFunctionUrlInvoke"
  action                 = "lambda:InvokeFunctionUrl"
  function_name          = aws_lambda_function.orchestrator.function_name
  principal              = "*"
  function_url_auth_type = "NONE"

  depends_on = [aws_lambda_permission.orchestrator_function_url_invoke]
}
