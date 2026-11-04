# The TPF sweep queries Await deadlines and continuations before dispatching due work.
resource "aws_dynamodb_table" "recovery_await" {
  name         = "${var.name_prefix}-recovery-await"
  billing_mode = "PAY_PER_REQUEST"
  hash_key     = "tenant_id"
  range_key    = "interaction_id"

  dynamic "attribute" {
    for_each = {
      tenant_id                       = "S"
      interaction_id                  = "S"
      query_deadline_key              = "S"
      query_deadline_sort             = "S"
      query_continuation_key          = "S"
      query_continuation_due_epoch_ms = "N"
    }
    content {
      name = attribute.key
      type = attribute.value
    }
  }

  global_secondary_index {
    name            = "await-interaction-pending-by-deadline"
    hash_key        = "query_deadline_key"
    range_key       = "query_deadline_sort"
    projection_type = "ALL"
  }

  global_secondary_index {
    name            = "await-interaction-continuation-work"
    hash_key        = "query_continuation_key"
    range_key       = "query_continuation_due_epoch_ms"
    projection_type = "ALL"
  }
}

resource "aws_cloudwatch_metric_alarm" "dead_letter" {
  alarm_name          = "${var.name_prefix}-dead-letter"
  alarm_description   = "Search work messages are waiting in the dead-letter queue."
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  dimensions          = { QueueName = aws_sqs_queue.dead_letter.name }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
}

resource "aws_sqs_queue" "recovery_dead_letter" {
  name                    = "${var.name_prefix}-recovery-dead-letter"
  sqs_managed_sse_enabled = true
}

resource "aws_cloudwatch_metric_alarm" "recovery_dead_letter" {
  alarm_name          = "${var.name_prefix}-recovery-dead-letter"
  alarm_description   = "Search recovery ticks are waiting in the dead-letter queue."
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  dimensions          = { QueueName = aws_sqs_queue.recovery_dead_letter.name }
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
}

resource "aws_sqs_queue" "recovery" {
  name                       = "${var.name_prefix}-recovery"
  visibility_timeout_seconds = 360
  sqs_managed_sse_enabled    = true
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.recovery_dead_letter.arn
    maxReceiveCount     = 5
  })
}

resource "aws_cloudwatch_event_rule" "recovery" {
  name                = "${var.name_prefix}-recovery"
  schedule_expression = "rate(1 minute)"
}

resource "aws_sqs_queue_policy" "recovery" {
  queue_url = aws_sqs_queue.recovery.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Principal = { Service = "events.amazonaws.com" }
      Action    = "sqs:SendMessage"
      Resource  = aws_sqs_queue.recovery.arn
      Condition = { ArnEquals = { "aws:SourceArn" = aws_cloudwatch_event_rule.recovery.arn } }
    }]
  })
}

resource "aws_cloudwatch_event_target" "recovery" {
  rule       = aws_cloudwatch_event_rule.recovery.name
  arn        = aws_sqs_queue.recovery.arn
  depends_on = [aws_sqs_queue_policy.recovery]
}

resource "aws_lambda_function" "queue_recovery" {
  function_name    = "${var.name_prefix}-queue-recovery"
  role             = aws_iam_role.queue_exec.arn
  s3_bucket        = aws_s3_object.lambda_code["function-worker"].bucket
  s3_key           = aws_s3_object.lambda_code["function-worker"].key
  source_code_hash = filebase64sha256(local.lambda_packages["function-worker"].zip_path)
  runtime          = "java21"
  handler          = "io.quarkus.amazon.lambda.runtime.QuarkusStreamHandler::handleRequest"
  architectures    = ["x86_64"]
  memory_size      = 1536
  timeout          = 60

  environment {
    variables = merge(local.queue_environment, {
      QUARKUS_LAMBDA_HANDLER                               = "aws-durable-due-work"
      PIPELINE_ORCHESTRATOR_DYNAMO_AWAIT_INTERACTION_TABLE = aws_dynamodb_table.recovery_await.name
    })
  }

  depends_on = [aws_iam_role_policy_attachment.queue_logs, aws_iam_role_policy.queue_access]
}

resource "aws_lambda_event_source_mapping" "recovery" {
  event_source_arn        = aws_sqs_queue.recovery.arn
  function_name           = aws_lambda_function.queue_recovery.arn
  batch_size              = 1
  function_response_types = ["ReportBatchItemFailures"]
}
