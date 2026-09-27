package com.arcadiaeditions

import org.springframework.modulith.ApplicationModule
import org.springframework.modulith.PackageInfo

@PackageInfo
@ApplicationModule(displayName = "Arcadia Editions - Payments Processing", allowedDependencies = ["common"])
class ModuleMetadata
