resource "azurerm_storage_account" "tfstate-storage-account" {
  name                     = var.storage_account_name
  resource_group_name      = azurerm_resource_group.resource_group.name
  location                 = azurerm_resource_group.resource_group.location
  account_tier             = "Standard"
  account_replication_type = "LRS"
  tags                     = var.tags
}

resource "azurerm_storage_container" "tfstate-storage-account-container" {
  name                  = "${terraform.workspace}-tfstate"
  storage_account_name  = azurerm_storage_account.tfstate-storage-account.name
  container_access_type = "private"
}