// Copyright (c) 2019-2024, NVIDIA CORPORATION & AFFILIATES. All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions
// are met:
//  * Redistributions of source code must retain the above copyright
//    notice, this list of conditions and the following disclaimer.
//  * Redistributions in binary form must reproduce the above copyright
//    notice, this list of conditions and the following disclaimer in the
//    documentation and/or other materials provided with the distribution.
//  * Neither the name of NVIDIA CORPORATION nor the names of its
//    contributors may be used to endorse or promote products derived
//    from this software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS ``AS IS'' AND ANY
// EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
// PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR
// CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
// EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO,
// PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR
// PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY
// OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
// (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
// OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE

// This was adapted from simple_grpc_sequence_sync_infer_client.py


package clients;


import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import inference.GRPCInferenceServiceGrpc;
import inference.GRPCInferenceServiceGrpc.GRPCInferenceServiceBlockingStub;
import inference.GRPCInferenceServiceGrpc.GRPCInferenceServiceStub;
import inference.GrpcService.InferParameter;
import inference.GrpcService.InferTensorContents;
import inference.GrpcService.ModelInferRequest;
import inference.GrpcService.ModelInferResponse;
import inference.GrpcService.ModelStreamInferResponse;
import inference.GrpcService.ServerLiveRequest;
import inference.GrpcService.ServerLiveResponse;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import com.google.protobuf.Message;
import io.grpc.Channel;
import io.grpc.Grpc;
import io.grpc.InsecureChannelCredentials;
import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;


public class SimpleSequenceJavaClient {

    private static String kModelName = "simple_sequence";

    private static CountDownLatch asyncSend(ArrayList<Integer> request_list,
                                            GRPCInferenceServiceStub grpcStub,
                                            String sequence_id) {
        final CountDownLatch finishLatch = new CountDownLatch(1);
        StreamObserver<ModelInferRequest> requestObserver =
            grpcStub.modelStreamInfer(new StreamObserver<ModelStreamInferResponse>() {
          @Override
          public void onNext(ModelStreamInferResponse response) {
            System.out.println("Got message");
            System.out.println(response.getInferResponse().getRawOutputContentsList().get(0).asReadOnlyByteBuffer().order(ByteOrder.LITTLE_ENDIAN).asIntBuffer().get(0));
          }

          @Override
          public void onError(Throwable t) {
            System.out.println("RouteChat Failed");
            System.out.println(Status.fromThrowable(t));
            System.out.println(t);
            finishLatch.countDown();
          }

          @Override
          public void onCompleted() {
            System.out.println("Finished ModelStreamInfer");
            finishLatch.countDown();
          }
        });

        ListIterator<Integer> iterator = request_list.listIterator();
        while (iterator.hasNext()) {
            int i = iterator.nextIndex();
            int request_data = iterator.next();
            ModelInferRequest.Builder request = ModelInferRequest.newBuilder();
            request.setModelName(kModelName);
            request.setModelVersion("");

            ModelInferRequest.InferInputTensor.Builder input = ModelInferRequest.InferInputTensor
                .newBuilder();
            input.setName("INPUT");
            input.setDatatype("INT32");
            input.addShape(1);
            input.addShape(1);
            input.setContents(InferTensorContents.newBuilder().addAllIntContents(Collections.nCopies(1, request_data)));

            request.addInputs(0, input.build());


            ModelInferRequest.InferRequestedOutputTensor.Builder output =
                ModelInferRequest.InferRequestedOutputTensor.newBuilder();
            output.setName("OUTPUT");
            request.addOutputs(0, output.build());

            request.putParameters("sequence_id", InferParameter.newBuilder().setStringParam(sequence_id).build())
                .putParameters("sequence_start", InferParameter.newBuilder().setBoolParam(i == 0).build())
                .putParameters("sequence_end", InferParameter.newBuilder().setBoolParam(!iterator.hasNext()).build());

            requestObserver.onNext(request.build());
        }
        requestObserver.onCompleted();
        return finishLatch;
    }

	public static void main(String[] args) {
		String host = args.length > 0 ? args[0] : "localhost";
		int port = args.length > 1 ? Integer.parseInt(args[1]) : 8001;

		String model_name = "simple_sequence";
		String model_version = "";

        ArrayList<Integer> values = new ArrayList<Integer>(Arrays.asList(11, 7, 5, 3, 2, 0, 1));

        ArrayList<Integer> int_result0_list = new ArrayList<Integer>();
        ArrayList<Integer> int_result1_list = new ArrayList<Integer>();

		ManagedChannel channel = ManagedChannelBuilder.forAddress(host, port).usePlaintext().build();
		GRPCInferenceServiceStub grpcStub = GRPCInferenceServiceGrpc.newStub(channel);
        GRPCInferenceServiceBlockingStub grpcBlockingStub = GRPCInferenceServiceGrpc.newBlockingStub(channel);

		// check server is live
		ServerLiveRequest serverLiveRequest = ServerLiveRequest.getDefaultInstance();
		ServerLiveResponse r = grpcBlockingStub.serverLive(serverLiveRequest);
		System.out.println(r);

        ArrayList<CountDownLatch> latches = new ArrayList<CountDownLatch>();

        for (int sequence_id = 1000; sequence_id < 1002; sequence_id++) {
            CountDownLatch latch = asyncSend(values, grpcStub, Integer.toString(sequence_id));
            latches.add(latch);
        }

        for(CountDownLatch latch: latches) {
            try {
                latch.await();
            } catch(InterruptedException e) {
            }
        }
		channel.shutdownNow();
	}
}
