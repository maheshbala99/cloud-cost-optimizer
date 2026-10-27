# Cloud Cost Optimizer Dashboard

A full-stack AWS cost analysis and optimization tool built with **Spring Boot**, **AWS SDK v2 (Cost Explorer + CloudWatch + EC2)**, **React + Recharts**, **Terraform**, and **Docker**.

## Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    React Dashboard                               │
│  KPI Cards | Daily Trend | Service Breakdown | Recommendations  │
└────────────────────────────┬────────────────────────────────────┘
                             │ REST API
                             ▼
┌─────────────────────────────────────────────────────────────────┐
│                  Spring Boot Backend                             │
│                                                                  │
│  CostController ──► CostExplorerService ──► AWS Cost Explorer   │
│                  └──► ResourceOptimizationService               │
│                             │                                    │
│                      CloudWatch API (CPU metrics)               │
│                      EC2 API (instance/volume metadata)         │
│                             │                                    │
│                    Caffeine Cache (1hr TTL)                     │
└─────────────────────────────────────────────────────────────────┘
                             │
                    Deploy via Terraform
                    (ECS Fargate + ALB)
```

## Tech Stack

| Layer | Technology |
|---|---|
| Backend | Java 17, Spring Boot 3.2, AWS SDK v2 |
| AWS APIs | Cost Explorer, CloudWatch, EC2 |
| Caching | Caffeine (in-memory, 1hr TTL) |
| Frontend | React 18, Recharts, Axios |
| Infrastructure | Terraform (ECS Fargate, ALB, IAM) |
| DevOps | Docker, Docker Compose |

## Features

### Dashboard
- **KPI Row** — Current month spend, previous month, potential savings, top service
- **Daily spend trend** — Line chart showing day-by-day costs for the current month
- **6-month history** — Bar chart showing month-over-month trend
- **Service breakdown** — Pie chart + sortable table of cost by AWS service
- **Optimization recommendations** — Filterable panel with actionable insights

### Optimization Engine
Automatically detects:
- **Idle EC2 instances** — average CPU < 5% over 14 days → stop/terminate
- **Oversized EC2 instances** — average CPU < 20% over 14 days → right-size
- **Unattached EBS volumes** — volumes in "available" state → delete
- **Reserved Instance opportunities** — on-demand instances > 30 days → convert to RI

### Infrastructure
- Terraform provisions ECS Fargate, ALB, IAM roles with least-privilege Cost Explorer + EC2 read permissions
- Caffeine caching prevents repeated Cost Explorer API charges (~$0.01/request)

## Getting Started

### Prerequisites
- Java 17, Maven 3.9+
- Node.js 18+
- Docker + Docker Compose
- AWS account with Cost Explorer enabled
- AWS credentials with appropriate permissions

### AWS Permissions Required
```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "ce:GetCostAndUsage",
        "ec2:DescribeInstances",
        "ec2:DescribeVolumes",
        "cloudwatch:GetMetricStatistics"
      ],
      "Resource": "*"
    }
  ]
}
```

### Run with Docker

```bash
git clone https://github.com/balamahesh/cloud-cost-optimizer
cd cloud-cost-optimizer

# Set your AWS credentials
export AWS_ACCESS_KEY_ID=your_key
export AWS_SECRET_ACCESS_KEY=your_secret
export AWS_REGION=us-east-1

docker-compose up --build
```

- Dashboard: http://localhost:3000
- API: http://localhost:8080

### Run locally

```bash
# Backend
cd backend
AWS_ACCESS_KEY_ID=your_key AWS_SECRET_ACCESS_KEY=your_secret mvn spring-boot:run

# Frontend
cd frontend
npm install && npm start
```

### Deploy to AWS with Terraform

```bash
cd terraform

# Create a terraform.tfvars file:
cat > terraform.tfvars << EOF
vpc_id             = "vpc-xxxxx"
subnet_ids         = ["subnet-xxxxx", "subnet-yyyyy"]
ecr_repository_url = "123456789.dkr.ecr.us-east-1.amazonaws.com/cloud-cost-optimizer"
EOF

terraform init
terraform plan
terraform apply
```

## API Reference

```http
GET /api/costs/summary          # Full dashboard payload
GET /api/costs/history?months=6 # Monthly totals for trend chart
GET /api/costs/recommendations  # Optimization recommendations only
GET /actuator/health            # Health check
```

**Example response — `/api/costs/summary`:**
```json
{
  "currentMonthTotal": 1847.32,
  "previousMonthTotal": 1623.55,
  "changePercentage": 13.8,
  "serviceBreakdown": [
    { "serviceName": "Amazon EC2", "amount": 892.10, "percentage": 48.3 },
    { "serviceName": "Amazon RDS", "amount": 412.50, "percentage": 22.3 }
  ],
  "recommendations": [
    {
      "resourceId": "i-0abc123def456",
      "resourceType": "EC2",
      "type": "IDLE_RESOURCE",
      "description": "Instance has 2.3% average CPU over 14 days",
      "estimatedMonthlySavings": 70.08,
      "priority": "HIGH"
    }
  ],
  "estimatedMonthlySavings": 312.45
}
```

## Project Structure

```
cloud-cost-optimizer/
├── backend/
│   ├── src/main/java/com/mahesh/costoptimizer/
│   │   ├── config/          # AWS SDK, Cache config
│   │   ├── controller/      # REST endpoints
│   │   ├── model/           # CostSummary DTOs
│   │   └── service/         # CostExplorer + Optimization services
│   ├── Dockerfile
│   └── pom.xml
├── frontend/
│   ├── src/
│   │   ├── pages/           # Dashboard (main page)
│   │   ├── components/      # RecommendationsPanel
│   │   └── services/        # API client
│   ├── Dockerfile
│   └── package.json
├── terraform/
│   ├── main.tf              # ECS, ALB, IAM resources
│   ├── variables.tf
│   └── outputs.tf
└── docker-compose.yml
```

## Key Design Decisions

**AWS Cost Explorer vs billing export** — Cost Explorer API provides on-demand queries without needing S3/Athena setup. The trade-off is per-request cost (~$0.01), which the Caffeine cache mitigates by only querying once per hour.

**CloudWatch for idle detection** — Rather than relying solely on Cost Explorer's (limited) recommendation API, the app queries CloudWatch CPU metrics directly, giving more control over thresholds and time windows.

**Caffeine over Redis** — For a single-instance deployment, Caffeine in-memory cache is simpler and sufficient. The comment in `CacheConfig.java` shows how to swap to Redis for distributed caching if the service scales horizontally.

**Terraform least-privilege IAM** — The ECS task role grants only the specific Cost Explorer and EC2 read actions needed, following AWS least-privilege best practice.
