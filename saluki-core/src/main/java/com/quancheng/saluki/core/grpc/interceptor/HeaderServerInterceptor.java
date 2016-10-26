package com.quancheng.saluki.core.grpc.interceptor;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.InetSocketAddress;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.Gson;
import com.quancheng.saluki.core.common.RpcContext;
import com.quancheng.saluki.core.common.SalukiConstants;
import com.quancheng.saluki.core.grpc.utils.MarshallersUtils;

import io.grpc.ForwardingServerCall.SimpleForwardingServerCall;
import io.grpc.Metadata;
import io.grpc.ServerCall;
import io.grpc.ServerCall.Listener;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;

public class HeaderServerInterceptor implements ServerInterceptor {

    private static final Logger log = LoggerFactory.getLogger(HeaderServerInterceptor.class);

    @Override
    public <ReqT, RespT> Listener<ReqT> interceptCall(ServerCall<ReqT, RespT> call, final Metadata headers,
                                                      ServerCallHandler<ReqT, RespT> next) {
        InetSocketAddress remoteAddress = (InetSocketAddress) call.attributes().get(ServerCall.REMOTE_ADDR_KEY);
        RpcContext.getContext().setAttachment(SalukiConstants.REMOTE_ADDRESS, remoteAddress.getHostString());
        copyMetadataToThreadLocal(headers);
        return next.startCall(new SimpleForwardingServerCall<ReqT, RespT>(call) {

            @Override
            public void sendHeaders(Metadata responseHeaders) {
                super.sendHeaders(responseHeaders);
            }

            @Override
            public void close(Status status, Metadata trailers) {
                if (status.getCause() != null) {
                    StringWriter sw = new StringWriter();
                    status.getCause().printStackTrace(new PrintWriter(sw));
                    trailers.put(MarshallersUtils.GRPC_ERRORCAUSE_VALUE, sw.toString());
                }
                super.close(status, trailers);

            }
        }, headers);
    }

    private void copyMetadataToThreadLocal(Metadata headers) {
        byte[] attachmentsBytes = headers.get(MarshallersUtils.GRPC_CONTEXT_ATTACHMENTS);
        byte[] valuesByte = headers.get(MarshallersUtils.GRPC_CONTEXT_VALUES);
        try {
            if (attachmentsBytes != null) {
                Map<String, String> attachments = new Gson().fromJson(new String(attachmentsBytes), Map.class);
                RpcContext.getContext().setAttachments(attachments);
            }
            if (valuesByte != null) {
                Map<String, Object> values = new Gson().fromJson(new String(valuesByte), Map.class);
                for (Map.Entry<String, Object> entry : values.entrySet()) {
                    RpcContext.getContext().set(entry.getKey(), entry.getValue());
                }
            }
        } catch (Throwable e) {
            log.error(e.getMessage(), e);
        }
    }

}
