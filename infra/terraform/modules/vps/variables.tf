variable "project_name" {
  type = string
}

variable "region" {
  type = string
}

variable "availability_zone" {
  type = string
}

variable "instance_cpu" {
  type = number
}

variable "instance_ram_mb" {
  type = number
}

variable "instance_disk_mb" {
  type = number
}

variable "ssh_key_name" {
  type = string
}

variable "ssh_allowed_cidr" {
  type = string
}

variable "server_timezone" {
  type = string
}

variable "letsencrypt_email" {
  type = string
}

variable "app_user" {
  type = string
}

variable "api_fqdn" {
  type = string
}

