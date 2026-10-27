variable "app_name" {
  description = "Application name used for resource naming"
  type        = string
  default     = "cloud-cost-optimizer"
}

variable "environment" {
  description = "Deployment environment (dev, staging, prod)"
  type        = string
  default     = "prod"
}

variable "aws_region" {
  description = "AWS region to deploy resources"
  type        = string
  default     = "us-east-1"
}

variable "vpc_id" {
  description = "VPC ID where resources will be deployed"
  type        = string
}

variable "subnet_ids" {
  description = "List of subnet IDs for ECS service and ALB"
  type        = list(string)
}

variable "ecr_repository_url" {
  description = "ECR repository URL for the backend Docker image"
  type        = string
}

variable "image_tag" {
  description = "Docker image tag to deploy"
  type        = string
  default     = "latest"
}

variable "desired_count" {
  description = "Number of ECS task replicas to run"
  type        = number
  default     = 1
}
