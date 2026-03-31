package io.github.recrafter.lapis.layers.lowering.types

import com.squareup.kotlinpoet.ExperimentalKotlinPoetApi
import io.github.recrafter.lapis.extensions.common.lapisError
import io.github.recrafter.lapis.extensions.jp.JPTypeName
import io.github.recrafter.lapis.extensions.kp.KPLambdaTypeName
import io.github.recrafter.lapis.extensions.kp.buildKotlinParameter
import io.github.recrafter.lapis.extensions.kp.orUnit
import io.github.recrafter.lapis.extensions.quoted
import io.github.recrafter.lapis.layers.lowering.models.IrParameter

class IrLambdaTypeName(override val kotlin: KPLambdaTypeName) : IrTypeName(kotlin) {

    override val java: JPTypeName
        get() = lapisError(
            "Lambda type ${kotlin.toString().quoted()} is not supported in Java, " +
                "but was leaked into IR"
        )

    companion object {
        @OptIn(ExperimentalKotlinPoetApi::class)
        fun of(
            receiverTypeName: IrTypeName? = null,
            parameters: List<IrParameter> = emptyList(),
            returnTypeName: IrTypeName? = null,
            contextParameters: List<IrTypeName> = emptyList(),
        ): IrLambdaTypeName =
            IrLambdaTypeName(
                KPLambdaTypeName.get(
                    receiver = receiverTypeName?.kotlin,
                    parameters = parameters.map { buildKotlinParameter(it) },
                    returnType = returnTypeName?.kotlin.orUnit(),
                    contextParameters = contextParameters.map { it.kotlin }
                )
            )
    }
}
