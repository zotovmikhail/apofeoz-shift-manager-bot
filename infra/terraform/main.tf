locals {
  api_fqdn = var.api_subdomain == "@" ? var.domain : "${var.api_subdomain}.${var.domain}"
}

module "vps" {
  source = "./modules/vps"

  project_name      = var.project_name
  region            = var.region
  availability_zone = var.availability_zone
  instance_cpu      = var.instance_cpu
  instance_ram_mb   = var.instance_ram_mb
  instance_disk_mb  = var.instance_disk_mb
  ssh_key_name      = var.ssh_key_name
  ssh_allowed_cidr  = var.ssh_allowed_cidr
  server_timezone   = var.server_timezone
  letsencrypt_email = var.letsencrypt_email
  app_user          = var.app_user
  api_fqdn          = local.api_fqdn
}

data "twc_dns_zone" "zone" {
  count = var.manage_dns ? 1 : 0
  name  = var.domain
}

resource "twc_dns_rr" "api_a_record" {
  count = var.manage_dns ? 1 : 0

  zone_id = data.twc_dns_zone.zone[0].id
  name    = var.api_subdomain
  type    = "A"
  value   = module.vps.public_ipv4
}
