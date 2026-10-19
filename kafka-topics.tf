terraform {
  required_providers {
    kafka = {
      source  = "Mongey/kafka"
      version = "~> 0.5"
    }
  }
}

provider "kafka" {
  bootstrap_servers = ["localhost:9092"]
}

resource "kafka_topic" "flight_searched" {
  name               = "flight-searched"
  partitions         = 3
  replication_factor = 1

  config = {
    "retention.ms"   = "604800000"
    "cleanup.policy" = "delete"
  }
}