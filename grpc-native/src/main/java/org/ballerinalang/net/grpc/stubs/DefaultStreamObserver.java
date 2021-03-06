/*
 * Copyright (c) 2018, WSO2 Inc. (http://wso2.com) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.ballerinalang.net.grpc.stubs;

import io.ballerina.runtime.api.Runtime;
import io.ballerina.runtime.api.async.Callback;
import io.ballerina.runtime.api.types.AttachedFunctionType;
import io.ballerina.runtime.api.types.Type;
import io.ballerina.runtime.api.values.BError;
import io.ballerina.runtime.api.values.BObject;
import io.ballerina.runtime.types.AttachedFunction;
import org.ballerinalang.net.grpc.GrpcConstants;
import org.ballerinalang.net.grpc.Message;
import org.ballerinalang.net.grpc.MessageUtils;
import org.ballerinalang.net.grpc.ServiceResource;
import org.ballerinalang.net.grpc.Status;
import org.ballerinalang.net.grpc.StreamObserver;
import org.ballerinalang.net.grpc.callback.ClientCallableUnitCallBack;
import org.ballerinalang.net.grpc.exception.GrpcClientException;
import org.ballerinalang.net.grpc.exception.StatusRuntimeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import static org.ballerinalang.net.grpc.GrpcConstants.MESSAGE_HEADERS;
import static org.ballerinalang.net.grpc.GrpcConstants.ON_COMPLETE_METADATA;
import static org.ballerinalang.net.grpc.GrpcConstants.ON_ERROR_METADATA;
import static org.ballerinalang.net.grpc.GrpcConstants.ON_MESSAGE_METADATA;
import static org.ballerinalang.net.grpc.MessageUtils.getHeaderObject;

/**
 * This is Stream Observer Implementation for gRPC Client Call.
 *
 * @since 1.0.0
 */
public class DefaultStreamObserver implements StreamObserver {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultStreamObserver.class);
    private Map<String, ServiceResource> resourceMap = new HashMap<>();
    private Semaphore semaphore;
    
    public DefaultStreamObserver(Runtime runtime, BObject callbackService, Semaphore semaphore) throws
            GrpcClientException {
        if (callbackService == null) {
            throw new GrpcClientException("Error while building the connection. Listener Service does not exist");
        }
        for (AttachedFunctionType function : callbackService.getType().getAttachedFunctions()) {
            resourceMap.put(function.getName(), new ServiceResource(runtime, callbackService,
                    (AttachedFunction) function));
        }
        this.semaphore = semaphore;
    }
    
    @Override
    public void onNext(Message value) {
        ServiceResource resource = resourceMap.get(GrpcConstants.ON_MESSAGE_RESOURCE);
        if (resource == null) {
            String message = "Error in listener service definition. onNext resource does not exists";
            LOG.error(message);
            throw MessageUtils.getConnectorError(new StatusRuntimeException(Status
                    .fromCode(Status.Code.INTERNAL.toStatus().getCode()).withDescription(message)));
        }
        List<Type> signatureParams = resource.getParamTypes();
        Object[] paramValues = new Object[signatureParams.size() * 2];

        BObject headerObject = null;
        if (resource.isHeaderRequired()) {
            headerObject = getHeaderObject();
            headerObject.addNativeData(MESSAGE_HEADERS, value.getHeaders());
        }
        Object requestParam = value.getbMessage();
        if (requestParam != null) {
            paramValues[0] = requestParam;
            paramValues[1] = true;
        }
        if (headerObject != null && signatureParams.size() == 2) {
            paramValues[2] = headerObject;
            paramValues[3] = true;
        }
        try {
            semaphore.acquire();
            Callback callback = new ClientCallableUnitCallBack(semaphore);
            resource.getRuntime().invokeMethodAsync(resource.getService(), resource.getFunctionName(), null,
                                                    ON_MESSAGE_METADATA, callback,
                                                    null, paramValues);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String message = "Internal error occurred. The current thread got interrupted";
            LOG.error(message);
            throw MessageUtils.getConnectorError(new StatusRuntimeException(Status
                    .fromCode(Status.Code.INTERNAL.toStatus().getCode()).withDescription(message)));
        }
    }
    
    @Override
    public void onError(Message error) {
        ServiceResource onError = resourceMap.get(GrpcConstants.ON_ERROR_RESOURCE);
        if (onError == null) {
            String message = "Error in listener service definition. onError resource does not exists";
            LOG.error(message);
            throw MessageUtils.getConnectorError(new StatusRuntimeException(Status
                    .fromCode(Status.Code.INTERNAL.toStatus().getCode()).withDescription(message)));
        }
        List<Type> signatureParams = onError.getParamTypes();
        Object[] paramValues = new Object[signatureParams.size() * 2];
        BObject headerObject = null;
        if (onError.isHeaderRequired()) {
            headerObject = getHeaderObject();
            headerObject.addNativeData(MESSAGE_HEADERS, error.getHeaders());
        }

        BError errorStruct = MessageUtils.getConnectorError(error.getError());
        paramValues[0] = errorStruct;
        paramValues[1] = true;

        if (headerObject != null && signatureParams.size() == 2) {
            paramValues[2] = headerObject;
            paramValues[3] = true;
        }
        try {
            semaphore.acquire();
            Callback callback = new ClientCallableUnitCallBack(semaphore);
            onError.getRuntime().invokeMethodAsync(onError.getService(), onError.getFunctionName(), null,
                                                   ON_ERROR_METADATA, callback, null,
                                                   paramValues);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String message = "Internal error occurred. The current thread got interrupted";
            LOG.error(message);
            throw MessageUtils.getConnectorError(new StatusRuntimeException(Status
                    .fromCode(Status.Code.INTERNAL.toStatus().getCode()).withDescription(message)));
        }
    }
    
    @Override
    public void onCompleted() {
        ServiceResource onCompleted = resourceMap.get(GrpcConstants.ON_COMPLETE_RESOURCE);
        if (onCompleted == null) {
            String message = "Error in listener service definition. onCompleted resource does not exists";
            LOG.error(message);
            throw MessageUtils.getConnectorError(new StatusRuntimeException(Status
                    .fromCode(Status.Code.INTERNAL.toStatus().getCode()).withDescription(message)));
        }
        List<Type> signatureParams = onCompleted.getParamTypes();
        Object[] paramValues = new Object[signatureParams.size() * 2];
        try {
            semaphore.acquire();
            Callback callback = new ClientCallableUnitCallBack(semaphore);
            onCompleted.getRuntime().invokeMethodAsync(onCompleted.getService(), onCompleted.getFunctionName(),
                                                       null, ON_COMPLETE_METADATA, callback, null, paramValues);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            String message = "Internal error occurred. The current thread got interrupted";
            LOG.error(message);
            throw MessageUtils.getConnectorError(new StatusRuntimeException(Status
                    .fromCode(Status.Code.INTERNAL.toStatus().getCode()).withDescription(message)));
        }
    }
}
