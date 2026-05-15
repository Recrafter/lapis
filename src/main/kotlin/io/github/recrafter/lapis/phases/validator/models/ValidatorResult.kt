package io.github.recrafter.lapis.phases.validator.models

import io.github.recrafter.lapis.phases.validator.models.patches.Patch
import io.github.recrafter.lapis.phases.validator.models.schemas.Schema

class ValidatorResult(
    val schemas: List<Schema>,
    val patches: List<Patch>,
)
