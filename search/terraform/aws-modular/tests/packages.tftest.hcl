# Mocked apply only: no cloud credentials, API calls or resources are needed.
mock_provider "aws" {
  mock_data "aws_iam_policy_document" {
    defaults = {
      json = "{\"Version\":\"2012-10-17\",\"Statement\":[{\"Effect\":\"Allow\",\"Action\":\"sts:AssumeRole\",\"Principal\":{\"Service\":\"lambda.amazonaws.com\"}}]}"
    }
  }

  mock_resource "aws_iam_role" {
    defaults = {
      arn = "arn:aws:iam::123456789012:role/search-test-lambda-exec"
    }
  }

  mock_resource "aws_lambda_function_url" {
    defaults = {
      function_url = "https://other-step.lambda-url.us-east-2.on.aws/"
    }
  }

  mock_resource "aws_sqs_queue" {
    defaults = {
      arn = "arn:aws:sqs:us-east-2:123456789012:search-test-queue"
    }
  }

  mock_resource "aws_s3_bucket" {
    defaults = {
      id     = "search-test-code-staging"
      bucket = "search-test-code-staging"
    }
  }
}

variables {
  aws_region                = "us-east-2"
  name_prefix               = "search-test"
  worker_zip_path           = "tests/fixtures/worker.fixture"
  orchestrator_zip_path     = "tests/fixtures/orchestrator.fixture"
  crawl_source_zip_path     = "tests/fixtures/crawl-source.fixture"
  parse_document_zip_path   = "tests/fixtures/parse-document.fixture"
  tokenize_content_zip_path = "tests/fixtures/tokenize-content.fixture"
  embed_content_zip_path    = "tests/fixtures/embed-content.fixture"
  index_document_zip_path   = "tests/fixtures/index-document.fixture"
}

run "stage_all_packages_and_preserve_code_identity" {
  command = apply

  override_resource {
    target = aws_sqs_queue.work
    values = { arn = "arn:aws:sqs:us-east-2:123456789012:search-test-work" }
  }

  override_resource {
    target = aws_sqs_queue.recovery
    values = { arn = "arn:aws:sqs:us-east-2:123456789012:search-test-recovery" }
  }

  override_resource {
    target = aws_sqs_queue.recovery_dead_letter
    values = { arn = "arn:aws:sqs:us-east-2:123456789012:search-test-recovery-dead-letter" }
  }

  override_resource {
    target = aws_lambda_function_url.step["index-document-svc"]
    values = {
      function_url = "https://index-document.lambda-url.us-east-2.on.aws/"
    }
  }

  assert {
    condition = alltrue([
      for client in ["BUILD_SEARCH_INDEX_DOCUMENT", "SUMMARIZE_INDEX_WRITES"] :
      lookup(one(aws_lambda_function.orchestrator.environment).variables, "QUARKUS_REST_CLIENT_PROCESS_${client}_URL", "") == aws_lambda_function_url.step["index-document-svc"].function_url
    ])
    error_message = "Both generated index-document REST clients must use the index-document Lambda Function URL."
  }

  assert {
    condition = (
      one(aws_lambda_function.orchestrator.environment).variables["PIPELINE_ORCHESTRATOR_MODE"] == "QUEUE_ASYNC" &&
      one(aws_lambda_function.orchestrator.environment).variables["PIPELINE_ORCHESTRATOR_IDEMPOTENCY_POLICY"] == "CLIENT_KEY_REQUIRED" &&
      one(aws_lambda_function.queue_worker.environment).variables == tomap(merge(one(aws_lambda_function.orchestrator.environment).variables, { QUARKUS_LAMBDA_HANDLER = "aws-durable-work-item" })) &&
      aws_lambda_event_source_mapping.work.event_source_arn == aws_sqs_queue.work.arn &&
      aws_lambda_event_source_mapping.work.function_name == aws_lambda_function.queue_worker.arn &&
      aws_lambda_event_source_mapping.work.batch_size == 1 &&
      one(aws_lambda_function.orchestrator.environment).variables["PIPELINE_ORCHESTRATOR_SQS_LOCAL_LOOPBACK"] == "false" &&
      aws_dynamodb_table.queue["command-effect"].range_key == "revision" &&
      one([for key in aws_dynamodb_table.queue["command-effect"].attribute : key.type if key.name == "revision"]) == "N" &&
      aws_lambda_function.queue_worker.s3_key == aws_s3_object.lambda_code["function-worker"].key &&
      aws_lambda_function.queue_worker.s3_key != aws_lambda_function.orchestrator.s3_key
    )
    error_message = "Search HTTP submission and SQS execution must use separate packages and share the release and queue state."
  }

  assert {
    condition = (
      aws_cloudwatch_event_rule.recovery.schedule_expression == "rate(1 minute)" &&
      aws_cloudwatch_event_target.recovery.arn == aws_sqs_queue.recovery.arn &&
      aws_lambda_event_source_mapping.recovery.event_source_arn == aws_sqs_queue.recovery.arn &&
      aws_lambda_event_source_mapping.recovery.event_source_arn != aws_lambda_event_source_mapping.work.event_source_arn &&
      jsondecode(aws_sqs_queue.recovery.redrive_policy).maxReceiveCount == 5 &&
      jsondecode(aws_sqs_queue.recovery.redrive_policy).deadLetterTargetArn == aws_sqs_queue.recovery_dead_letter.arn &&
      aws_sqs_queue.recovery_dead_letter.sqs_managed_sse_enabled &&
      aws_cloudwatch_metric_alarm.recovery_dead_letter.dimensions["QueueName"] == aws_sqs_queue.recovery_dead_letter.name &&
      aws_lambda_event_source_mapping.recovery.function_name == aws_lambda_function.queue_recovery.arn &&
      aws_lambda_function.queue_recovery.s3_key == aws_lambda_function.queue_worker.s3_key &&
      one(aws_lambda_function.queue_recovery.environment).variables["QUARKUS_LAMBDA_HANDLER"] == "aws-durable-due-work" &&
      one(aws_lambda_function.queue_recovery.environment).variables["PIPELINE_ORCHESTRATOR_PROCESS_LOOPS_DISABLED"] == "true" &&
      one(aws_lambda_function.queue_recovery.environment).variables["PIPELINE_ORCHESTRATOR_DYNAMO_AWAIT_INTERACTION_TABLE"] == aws_dynamodb_table.recovery_await.name &&
      toset(aws_dynamodb_table.recovery_await.global_secondary_index[*].name) == toset(["await-interaction-pending-by-deadline", "await-interaction-continuation-work"]) &&
      aws_cloudwatch_metric_alarm.dead_letter.dimensions["QueueName"] == aws_sqs_queue.dead_letter.name
    )
    error_message = "Search must sweep due work from its dedicated trigger and alarm on its work DLQ."
  }

  assert {
    condition = alltrue([for name, function in aws_lambda_function.step :
      function.filename == null &&
      function.s3_bucket == aws_s3_bucket.lambda_code.id &&
      function.s3_key == aws_s3_object.lambda_code[name].key
    ])
    error_message = "Every step must load the exact hashed package through S3, not inline."
  }

  assert {
    condition = (
      aws_lambda_function.orchestrator.filename == null &&
      aws_lambda_function.orchestrator.s3_bucket == aws_s3_bucket.lambda_code.id &&
      aws_lambda_function.orchestrator.s3_key == aws_s3_object.lambda_code["orchestrator-svc"].key
    )
    error_message = "The orchestrator must use the same S3-backed package path as steps."
  }

  assert {
    # This oracle is independent of both the input assignments and production
    # locals: swapping package variables must not change the expected routing.
    condition = length(aws_s3_object.lambda_code) == 7 && alltrue([
      for name, fixture in {
        crawl-source-svc     = "tests/fixtures/crawl-source.fixture"
        parse-document-svc   = "tests/fixtures/parse-document.fixture"
        tokenize-content-svc = "tests/fixtures/tokenize-content.fixture"
        embed-content-svc    = "tests/fixtures/embed-content.fixture"
        index-document-svc   = "tests/fixtures/index-document.fixture"
        function-worker      = "tests/fixtures/worker.fixture"
        orchestrator-svc     = "tests/fixtures/orchestrator.fixture"
      } :
      output.search_release.artifacts[name].local_path == abspath("${path.root}/${fixture}") &&
      aws_s3_object.lambda_code[name].source == "${path.root}/${fixture}" &&
      aws_s3_object.lambda_code[name].key == "${name}/${filesha256("${path.root}/${fixture}")}/function.zip" &&
      aws_s3_object.lambda_code[name].source_hash == filesha256("${path.root}/${fixture}") &&
      (name == "orchestrator-svc" ? aws_lambda_function.orchestrator.source_code_hash : name == "function-worker" ? aws_lambda_function.queue_worker.source_code_hash : aws_lambda_function.step[name].source_code_hash) == filebase64sha256("${path.root}/${fixture}")
    ])
    error_message = "Each service must stage its independently expected fixture with matching object and Lambda hashes."
  }

  assert {
    condition = (
      aws_s3_bucket.lambda_code.force_destroy &&
      aws_s3_bucket_public_access_block.lambda_code.block_public_acls &&
      aws_s3_bucket_public_access_block.lambda_code.block_public_policy &&
      aws_s3_bucket_public_access_block.lambda_code.ignore_public_acls &&
      aws_s3_bucket_public_access_block.lambda_code.restrict_public_buckets &&
      one(one(aws_s3_bucket_server_side_encryption_configuration.lambda_code.rule).apply_server_side_encryption_by_default).sse_algorithm == "AES256"
    )
    error_message = "Staging must remain private, encrypted and removable with disposable deployment cleanup."
  }
}
