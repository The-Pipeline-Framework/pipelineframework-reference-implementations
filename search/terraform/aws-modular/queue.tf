locals {
  table_keys = {
    execution         = ["tenant_id", "execution_id"]
    execution-key     = ["tenant_execution_key"]
    execution-payload = ["payload_id", "payload_part"]
    command-effect    = ["command_key", "revision"]
    release           = ["registry_key", "registry_sort"]
  }
}

resource "aws_dynamodb_table" "queue" {
  for_each     = local.table_keys
  name         = "${var.name_prefix}-${each.key}"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = each.value[0]
  range_key    = try(each.value[1], null)
  dynamic "attribute" {
    for_each = toset(each.value)
    content {
      name = attribute.value
      type = attribute.value == "revision" ? "N" : "S"
    }
  }
}

resource "aws_sqs_queue" "dead_letter" {
  name                    = "${var.name_prefix}-dead-letter"
  sqs_managed_sse_enabled = true
}

resource "aws_sqs_queue" "work" {
  name                       = "${var.name_prefix}-work"
  visibility_timeout_seconds = 720
  sqs_managed_sse_enabled    = true
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.dead_letter.arn
    maxReceiveCount     = 3
  })
}

resource "aws_iam_role" "queue_exec" {
  name               = "${var.name_prefix}-queue-exec"
  assume_role_policy = data.aws_iam_policy_document.lambda_assume_role.json
}

resource "aws_iam_role_policy_attachment" "queue_logs" {
  role       = aws_iam_role.queue_exec.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AWSLambdaBasicExecutionRole"
}

resource "aws_iam_role_policy" "queue_access" {
  name = "search-queue"
  role = aws_iam_role.queue_exec.name
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Effect   = "Allow"
        Action   = ["sqs:SendMessage", "sqs:ReceiveMessage", "sqs:DeleteMessage", "sqs:GetQueueAttributes"]
        Resource = aws_sqs_queue.work.arn
      },
      {
        Effect   = "Allow"
        Action   = ["dynamodb:GetItem", "dynamodb:PutItem", "dynamodb:UpdateItem", "dynamodb:DeleteItem", "dynamodb:BatchGetItem", "dynamodb:BatchWriteItem", "dynamodb:Query", "dynamodb:Scan", "dynamodb:ConditionCheckItem"]
        Resource = [for table in aws_dynamodb_table.queue : table.arn]
      }
    ]
  })
}

resource "aws_lambda_function" "queue_worker" {
  function_name    = "${var.name_prefix}-queue-worker"
  role             = aws_iam_role.queue_exec.arn
  s3_bucket        = aws_s3_object.lambda_code["function-worker"].bucket
  s3_key           = aws_s3_object.lambda_code["function-worker"].key
  source_code_hash = filebase64sha256(local.lambda_packages["function-worker"].zip_path)
  runtime          = "java21"
  handler          = "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"
  architectures    = ["x86_64"]
  memory_size      = 1536
  timeout          = 120

  environment {
    variables = merge(local.queue_environment, { QUARKUS_LAMBDA_HANDLER = "aws-durable-work-item" })
  }

  depends_on = [aws_iam_role_policy_attachment.queue_logs, aws_iam_role_policy.queue_access]
}

resource "aws_lambda_event_source_mapping" "work" {
  event_source_arn        = aws_sqs_queue.work.arn
  function_name           = aws_lambda_function.queue_worker.arn
  batch_size              = 1
  function_response_types = ["ReportBatchItemFailures"]
}
