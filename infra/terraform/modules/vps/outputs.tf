output "server_id" {
  value = twc_server.main.id
}

output "public_ipv4" {
  value = twc_server.main.main_ipv4
}

output "firewall_id" {
  value = twc_firewall.main.id
}

