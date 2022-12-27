output "twitter_api_key_param" {
  value = aws_ssm_parameter.twitter_key.name
}

output "twitter_api_secret_param" {
  value = aws_ssm_parameter.twitter_secret.name
}

output "twitter_api_token_param" {
  value = aws_ssm_parameter.twitter_token.name
}