/*
 * Copyright 2019 Web3 Labs Ltd.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.web3j.eth2.api.client

import mu.KLogging
import org.glassfish.jersey.client.proxy.WebResourceFactory
import org.web3j.eth2.api.BeaconNodeApi
import org.web3j.eth2.api.schema.ErrorMessage
import java.lang.reflect.InvocationHandler
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Proxy
import java.net.URL
import java.util.EnumSet
import java.util.concurrent.CompletableFuture
import javax.ws.rs.ClientErrorException
import javax.ws.rs.client.ClientRequestContext
import javax.ws.rs.client.ClientRequestFilter
import javax.ws.rs.client.WebTarget
import javax.ws.rs.core.HttpHeaders
import javax.ws.rs.sse.SseEventSource

object BeaconClientFactory {

    /**
     * Builds a JAX-RS client with the given service and optional token.
     */
    @JvmStatic
    @JvmOverloads
    fun create(service: BeaconClientService, token: String? = null): BeaconNodeApi {
        val target = service.client.target(service.uri)
        token?.run { target.register(AuthenticationFilter(token)) }

        val client = WebResourceFactory.newResource(BeaconNodeApi::class.java, target)
        val handler = ClientInvocationHandler(target, client)

        @Suppress("UNCHECKED_CAST")
        return Proxy.newProxyInstance(
            BeaconNodeApi::class.java.classLoader,
            arrayOf(BeaconNodeApi::class.java),
            handler
        ) as BeaconNodeApi
    }

    /**
     * Extension value to deserialize error message from a response.
     */
    val ClientErrorException.errorMessage: ErrorMessage
        get() = response.readEntity(ErrorMessage::class.java)

    /**
     * Unmarshall error message from a response.
     */
    @JvmStatic
    fun unmarshall(exception: ClientErrorException): ErrorMessage =
        exception.response.readEntity(ErrorMessage::class.java)

    /**
     * Invocation handler for proxied resources.
     *
     * Handles contract events using a Server-Sent Event (SSE) request.
     *
     * Also implements an exception mapping mechanism to avoid reporting
     * [ClientErrorException]s to the client.
     */
    private class ClientInvocationHandler(
        private val target: WebTarget,
        private val client: Any
    ) : InvocationHandler {

        override fun invoke(proxy: Any, method: Method, args: Array<out Any>?): Any? {
            return if (method.isEvent()) {
                logger.debug { "Invoking event method: $method" }
                invokeOnEvent(args!![0])
            } else {
                logger.debug { "Invoking client method: $method" }
                invokeClient(method, args)
            }
        }

        private fun invokeOnEvent(onEvent: Any): CompletableFuture<Void> {
            @Suppress("UNCHECKED_CAST")
            val eventType = (onEvent as (Any) -> Unit).typeArguments[0]
            val source = SseEventSource.target(clientTarget()).build()
            return SseEventSourceResult(source, onEvent, eventType).also {
                it.open()
            }
        }

        private fun invokeClient(method: Method, args: Array<out Any>?): Any {
            try {
                // Invoke the original method on the client
                return method.invoke(client, *(args ?: arrayOf())).let {
                    if (Proxy.isProxyClass(it.javaClass)) {
                        // The result is a Jersey web resource
                        // so we need to wrap it again
                        Proxy.newProxyInstance(
                            method.returnType.classLoader,
                            arrayOf(method.returnType),
                            ClientInvocationHandler(target, it)
                        )
                    } else {
                        it
                    }
                }
            } catch (e: InvocationTargetException) {
                throw handleInvocationException(e, method)
            } catch (e: ClientErrorException) {
                throw handleClientError(e, method)
            }
        }

        private fun handleInvocationException(error: InvocationTargetException, method: Method): Throwable {
            return error.targetException.let {
                if (it is ClientErrorException) {
                    handleClientError(it, method)
                } else {
                    logger.error {
                        "Unexpected exception while invoking method $method: " +
                                (error.message ?: error::class.java.canonicalName)
                    }
                    it
                }
            }
        }

        private fun handleClientError(error: ClientErrorException, method: Method): RuntimeException {
            logger.error {
                "Client exception while invoking method $method: " +
                        (error.message ?: error.response.statusInfo.reasonPhrase)
            }
            return BeaconClientException.of(error)
        }

        private fun clientTarget(): WebTarget {
            val resourcePath = client.toString()
                .removePrefix("JerseyWebTarget { ")
                .removeSuffix(" }")
                .run { URL(this).path }
            return target.path(resourcePath)
        }

        private fun Method.isEvent() = parameterTypes.size == 2 &&
                parameterTypes[0] == EnumSet::class.java &&
                parameterTypes[1] == Function1::class.java &&
                returnType == CompletableFuture::class.java

        private val Any.typeArguments: List<Class<*>>
            get() {
                val parameterizedType = this::class.java.genericInterfaces[0] as ParameterizedType
                return parameterizedType.actualTypeArguments.map { it as Class<*> }
            }

        private class SseEventSourceResult<T>(
            private val source: SseEventSource,
            onEvent: (T) -> Unit,
            eventType: Class<T>
        ) : CompletableFuture<Void>() {
            init {
                source.register(
                    { onEvent.invoke(it.readData(eventType)) },
                    { completeExceptionally(it) },
                    { complete(null) }
                )
                whenComplete { _, _ ->
                    // Close the source gracefully by client
                    if (source.isOpen) source.close()
                }
            }

            fun open() {
                Thread {
                    source.open()
                    while (source.isOpen) {
                        logger.debug { "Listening on event source..." }
                        Thread.sleep(5000)
                    }
                }.start()
            }
        }

        companion object : KLogging()
    }

    private class AuthenticationFilter(private val token: String) : ClientRequestFilter {

        override fun filter(requestContext: ClientRequestContext) {
            requestContext.headers.putSingle(HttpHeaders.AUTHORIZATION, "Bearer $token")
        }
    }
}
