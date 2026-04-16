output "server_id" {
  value       = module.vps.server_id
  description = "Timeweb server ID."
}

output "server_public_ipv4" {
  value       = module.vps.public_ipv4
  description = "Server public IPv4."
}

output "api_url" {
  value       = "https://${local.api_fqdn}"
  description = "Public API URL."
}

output "admin_url" {
  value       = "https://${local.admin_fqdn}"
  description = "Public admin web URL."
}

output "ssh_connect" {
  value       = "ssh ${var.app_user}@${module.vps.public_ipv4}"
  description = "SSH command."
}

output "next_steps" {
  value = [
    "1) Copy infra/deploy/env.example.prod to .env.prod and fill secrets.",
    "2) Upload infra/deploy files to /opt/apofeoz/config and /opt/apofeoz/scripts on server.",
    "3) Clone repository to /opt/apofeoz/app and run /opt/apofeoz/scripts/deploy-prod.sh.",
    "4) Enable TLS by running /opt/apofeoz/scripts/setup-tls.sh ${local.api_fqdn} ${local.admin_fqdn} ${var.letsencrypt_email}.",
  ]
  description = "Post-apply manual deploy checklist."
}
