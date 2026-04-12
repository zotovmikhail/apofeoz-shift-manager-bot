locals {
  server_name   = "${var.project_name}-prod"
  firewall_name = "${var.project_name}-prod-fw"
}

data "twc_configurator" "main" {
  location    = var.region
  preset_type = "standard"
}

data "twc_os" "ubuntu" {
  name    = "ubuntu"
  version = "22.04"
}

data "twc_ssh_keys" "main" {
  name = var.ssh_key_name
}

resource "twc_server" "main" {
  name  = local.server_name
  os_id = tonumber(data.twc_os.ubuntu.id)

  availability_zone = var.availability_zone == "" ? null : var.availability_zone
  ssh_keys_ids      = [tonumber(data.twc_ssh_keys.main.id)]

  configuration {
    configurator_id = tonumber(data.twc_configurator.main.id)
    cpu             = var.instance_cpu
    ram             = var.instance_ram_mb
    disk            = var.instance_disk_mb
  }

  cloud_init = templatefile("${path.module}/../../templates/cloud-init.yaml.tftpl", {
    timezone          = var.server_timezone
    app_user          = var.app_user
    api_fqdn          = var.api_fqdn
    letsencrypt_email = var.letsencrypt_email
  })
}

resource "twc_firewall" "main" {
  name = local.firewall_name

  link {
    id   = twc_server.main.id
    type = "server"
  }
}

resource "twc_firewall_rule" "ssh" {
  firewall_id = twc_firewall.main.id
  direction   = "ingress"
  protocol    = "tcp"
  port        = 22
  cidr        = var.ssh_allowed_cidr
  description = "Allow SSH"
}

resource "twc_firewall_rule" "http" {
  firewall_id = twc_firewall.main.id
  direction   = "ingress"
  protocol    = "tcp"
  port        = 80
  cidr        = "0.0.0.0/0"
  description = "Allow HTTP for ACME and redirect"
}

resource "twc_firewall_rule" "https" {
  firewall_id = twc_firewall.main.id
  direction   = "ingress"
  protocol    = "tcp"
  port        = 443
  cidr        = "0.0.0.0/0"
  description = "Allow HTTPS"
}
