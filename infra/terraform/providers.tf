provider "twc" {
  token = var.twc_token == "" ? null : var.twc_token
}

