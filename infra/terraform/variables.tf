variable "twc_token" {
  type        = string
  description = "Timeweb API token. Prefer TWC_TOKEN env var."
  sensitive   = true
  default     = ""
}

variable "project_name" {
  type        = string
  description = "Resource name prefix."
  default     = "apofeoz"
}

variable "domain" {
  type        = string
  description = "Primary DNS zone, e.g. example.com."
}

variable "api_subdomain" {
  type        = string
  description = "API subdomain label. Use '@' for zone root."
  default     = "api"
}

variable "manage_dns" {
  type        = bool
  description = "Create/update A record in Timeweb DNS zone."
  default     = true
}

variable "region" {
  type        = string
  description = "Server location."
  default     = "ru-1"
}

variable "availability_zone" {
  type        = string
  description = "Optional availability zone (msk-1, spb-1, etc). Empty means provider default."
  default     = ""
}

variable "instance_cpu" {
  type        = number
  description = "VPS CPU cores."
  default     = 1
}

variable "instance_ram_mb" {
  type        = number
  description = "VPS RAM in MB."
  default     = 2048
}

variable "instance_disk_mb" {
  type        = number
  description = "VPS disk in MB."
  default     = 30720
}

variable "ssh_key_name" {
  type        = string
  description = "Existing Timeweb SSH key name to install on server."
}

variable "ssh_allowed_cidr" {
  type        = string
  description = "CIDR allowed for SSH in firewall."
  default     = "0.0.0.0/0"
}

variable "server_timezone" {
  type        = string
  description = "Server timezone for logs and cron timestamps."
  default     = "Europe/Moscow"
}

variable "letsencrypt_email" {
  type        = string
  description = "Email for Let's Encrypt registration."
}

variable "app_user" {
  type        = string
  description = "OS user owning deploy/runtime directories."
  default     = "deploy"
}

