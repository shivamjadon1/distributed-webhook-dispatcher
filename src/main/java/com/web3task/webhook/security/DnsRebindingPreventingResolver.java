package com.web3task.webhook.security;

import io.netty.channel.EventLoop;
import io.netty.resolver.AddressResolver;
import io.netty.resolver.AddressResolverGroup;
import io.netty.resolver.DefaultNameResolver;
import io.netty.resolver.NameResolver;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.Promise;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;

public class DnsRebindingPreventingResolver extends AddressResolverGroup<InetSocketAddress> {

    private static final Logger log = LoggerFactory.getLogger(DnsRebindingPreventingResolver.class);
    private final SafeUrlValidator safeUrlValidator;
    private final boolean allowLoopbackForTesting;

    public DnsRebindingPreventingResolver(SafeUrlValidator safeUrlValidator, boolean allowLoopbackForTesting) {
        this.safeUrlValidator = safeUrlValidator;
        this.allowLoopbackForTesting = allowLoopbackForTesting;
    }

    @Override
    protected AddressResolver<InetSocketAddress> newResolver(EventExecutor executor) throws Exception {
        return new ValidatingAddressResolver(executor, safeUrlValidator, allowLoopbackForTesting);
    }

    private static class ValidatingAddressResolver implements AddressResolver<InetSocketAddress> {
        private final EventExecutor executor;
        private final SafeUrlValidator safeUrlValidator;
        private final boolean allowLoopbackForTesting;

        public ValidatingAddressResolver(EventExecutor executor, SafeUrlValidator safeUrlValidator, boolean allowLoopbackForTesting) {
            this.executor = executor;
            this.safeUrlValidator = safeUrlValidator;
            this.allowLoopbackForTesting = allowLoopbackForTesting;
        }

        @Override
        public boolean isSupported(java.net.SocketAddress address) {
            return address instanceof InetSocketAddress;
        }

        @Override
        public boolean isResolved(java.net.SocketAddress address) {
            return address instanceof InetSocketAddress inet && !inet.isUnresolved();
        }

        @Override
        public io.netty.util.concurrent.Future<InetSocketAddress> resolve(java.net.SocketAddress address) {
            return resolve(address, executor.newPromise());
        }

        @Override
        public io.netty.util.concurrent.Future<InetSocketAddress> resolve(
                java.net.SocketAddress address, Promise<InetSocketAddress> promise) {
            if (!(address instanceof InetSocketAddress inet)) {
                return promise.setFailure(new IllegalArgumentException("Unsupported address: " + address));
            }
            if (!inet.isUnresolved()) {
                if (!allowLoopbackForTesting && safeUrlValidator.isDisallowedIpAddress(inet.getAddress())) {
                    return promise.setFailure(new SecurityException("Blocked resolved restricted IP: " + inet.getAddress()));
                }
                return promise.setSuccess(inet);
            }

            try {
                InetAddress[] resolved = InetAddress.getAllByName(inet.getHostString());
                for (InetAddress addr : resolved) {
                    if (!allowLoopbackForTesting && safeUrlValidator.isDisallowedIpAddress(addr)) {
                        return promise.setFailure(new SecurityException(
                                "DNS Rebinding defense blocked connection to restricted IP: " + addr.getHostAddress()));
                    }
                }
                return promise.setSuccess(new InetSocketAddress(resolved[0], inet.getPort()));
            } catch (Exception e) {
                return promise.setFailure(e);
            }
        }

        @Override
        public io.netty.util.concurrent.Future<List<InetSocketAddress>> resolveAll(java.net.SocketAddress address) {
            return resolveAll(address, executor.newPromise());
        }

        @Override
        public io.netty.util.concurrent.Future<List<InetSocketAddress>> resolveAll(
                java.net.SocketAddress address, Promise<List<InetSocketAddress>> promise) {
            if (!(address instanceof InetSocketAddress inet)) {
                return promise.setFailure(new IllegalArgumentException("Unsupported address: " + address));
            }
            try {
                InetAddress[] resolved = InetAddress.getAllByName(inet.getHostString());
                List<InetSocketAddress> list = new ArrayList<>();
                for (InetAddress addr : resolved) {
                    if (!allowLoopbackForTesting && safeUrlValidator.isDisallowedIpAddress(addr)) {
                        return promise.setFailure(new SecurityException(
                                "DNS Rebinding defense blocked connection to restricted IP: " + addr.getHostAddress()));
                    }
                    list.add(new InetSocketAddress(addr, inet.getPort()));
                }
                return promise.setSuccess(list);
            } catch (Exception e) {
                return promise.setFailure(e);
            }
        }

        @Override
        public void close() {
        }
    }
}
