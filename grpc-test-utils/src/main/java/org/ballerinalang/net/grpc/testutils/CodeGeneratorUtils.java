/*
 *  Copyright (c) 2020, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.ballerinalang.net.grpc.testutils;

import org.ballerinalang.jvm.values.api.BString;
import org.ballerinalang.net.grpc.protobuf.cmd.GrpcCmd;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * This contains Utility functions to generate Ballerina code from Protobuf contract.
 */
public class CodeGeneratorUtils {

    public static void generateSourceCode(BString sProtoFilePath, BString sOutputDirPath, Object mode) {

        Class<?> grpcCmdClass;
        try {
            grpcCmdClass = Class.forName("org.ballerinalang.net.grpc.protobuf.cmd.GrpcCmd");
            GrpcCmd grpcCmd = (GrpcCmd) grpcCmdClass.newInstance();
            Path protoFilePath = Paths.get(sProtoFilePath.getValue());
            Path outputDirPath = Paths.get(sOutputDirPath.getValue());
            grpcCmd.setBalOutPath(outputDirPath.toAbsolutePath().toString());
            grpcCmd.setProtoPath(protoFilePath.toAbsolutePath().toString());
            if (mode instanceof BString) {
                grpcCmd.setMode(((BString)mode).getValue());
            }
            grpcCmd.execute();
        } catch (ClassNotFoundException | IllegalAccessException | InstantiationException e) {
            throw new RuntimeException(e);
        }
    }
}
