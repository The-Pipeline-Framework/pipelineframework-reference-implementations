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
  orchestrator_zip_path     = "tests/package.fixture"
  crawl_source_zip_path     = "tests/package.fixture"
  parse_document_zip_path   = "tests/package.fixture"
  tokenize_content_zip_path = "tests/package.fixture"
  embed_content_zip_path    = "tests/package.fixture"
  index_document_zip_path   = "tests/package.fixture"
}

run "stage_all_packages_and_preserve_code_identity" {
  command = apply

  assert {
    condition = alltrue([for name, function in aws_lambda_function.step :
      function.filename == null &&
      function.s3_bucket == aws_s3_bucket.lambda_code.id &&
      function.s3_key == aws_s3_object.lambda_code[name].key &&
      function.source_code_hash == filebase64sha256(local.step_functions[name].zip_path)
    ])
    error_message = "Every step must load the exact hashed package through S3, not inline."
  }

  assert {
    condition = (
      aws_lambda_function.orchestrator.filename == null &&
      aws_lambda_function.orchestrator.s3_bucket == aws_s3_bucket.lambda_code.id &&
      aws_lambda_function.orchestrator.s3_key == aws_s3_object.lambda_code["orchestrator-svc"].key &&
      aws_lambda_function.orchestrator.source_code_hash == filebase64sha256(local.orchestrator_zip_path)
    )
    error_message = "The orchestrator must use the same S3-backed package path as steps."
  }

  assert {
    condition = length(aws_s3_object.lambda_code) == 6 && alltrue([
      for name, object in aws_s3_object.lambda_code :
      object.key == "${name}/${filesha256(local.lambda_packages[name].zip_path)}/function.zip" &&
      object.source_hash == filesha256(local.lambda_packages[name].zip_path)
    ])
    error_message = "All six packages must have content-addressed keys and matching source hashes."
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
