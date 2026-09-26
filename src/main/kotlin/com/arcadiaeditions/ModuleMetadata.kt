package com.arcadiaeditions

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(displayName = "Arcadia Editions - Catalog Inventory", allowedDependencies = ["common"])
class ModuleMetadata
