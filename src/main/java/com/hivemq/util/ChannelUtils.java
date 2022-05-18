/*
 * Copyright 2019-present HiveMQ GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.hivemq.util;

import com.hivemq.bootstrap.ClientConnection;
import com.hivemq.bootstrap.ClientState;
import com.hivemq.configuration.service.InternalConfigurations;
import com.hivemq.configuration.service.entity.Listener;
import com.hivemq.extension.sdk.api.annotations.NotNull;
import com.hivemq.extension.sdk.api.annotations.Nullable;
import com.hivemq.security.auth.ClientToken;
import com.hivemq.security.auth.SslClientCertificate;
import io.netty.channel.Channel;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Various utilities for working with channels
 */
public final class ChannelUtils {

    private ChannelUtils() {
        //This is a utility class, don't instantiate it!
    }

    public static Optional<String> getChannelIP(final Channel channel) {

        final Optional<InetAddress> inetAddress = getChannelAddress(channel);

        return inetAddress.map(InetAddress::getHostAddress);

    }

    public static Optional<InetAddress> getChannelAddress(final Channel channel) {

        final Optional<SocketAddress> socketAddress = Optional.ofNullable(channel.remoteAddress());
        if (socketAddress.isPresent()) {
            final SocketAddress sockAddress = socketAddress.get();
            //If this is not an InetAddress, we're treating this as if there's no address
            if (sockAddress instanceof InetSocketAddress) {
                return Optional.ofNullable(((InetSocketAddress) sockAddress).getAddress());
            }
        }
        return Optional.empty();
    }

    /**
     * Fetches the clientId from the channel attributes of the passed channel
     */
    public static String getClientId(final @NotNull Channel channel) {
        return channel.attr(ChannelAttributes.CLIENT_CONNECTION).get().getClientId();
    }

    public static ClientToken tokenFromChannel(@NotNull final Channel channel, @NotNull final Long disconnectTimestamp) {
        checkNotNull(disconnectTimestamp, "disconnectTimestamp must not be null");
        return getClientToken(channel, disconnectTimestamp);
    }

    public static ClientToken tokenFromChannel(@NotNull final Channel channel) {
        return getClientToken(channel, null);
    }

    public static boolean messagesInFlight(@NotNull final Channel channel) {
        final ClientConnection clientConnection = channel.attr(ChannelAttributes.CLIENT_CONNECTION).get();
        final boolean inFlightMessagesSent = clientConnection.isInFlightMessagesSent();
        if (!inFlightMessagesSent) {
            return true;
        }
        final AtomicInteger inFlightMessages = clientConnection.getInFlightMessages();
        if (inFlightMessages == null) {
            return false;
        }
        return inFlightMessages.get() > 0;
    }

    public static int maxInflightWindow(@NotNull final Channel channel) {
        final Integer clientReceiveMaximum = channel.attr(ChannelAttributes.CLIENT_CONNECTION).get().getClientReceiveMaximum();
        final int max = InternalConfigurations.MAX_INFLIGHT_WINDOW_SIZE;
        if (clientReceiveMaximum == null) {
            return max;
        }
        return Math.min(clientReceiveMaximum, max);
    }

    private static ClientToken getClientToken(@NotNull final Channel channel, @Nullable final Long disconnectTimestamp) {
        checkNotNull(channel, "channel must not be null");

        final ClientConnection clientConnection = channel.attr(ChannelAttributes.CLIENT_CONNECTION).get();

        final String clientId = getClientId(channel);
        //These things can all be null!
        final String username = clientConnection.getAuthUsername();
        final byte[] password = clientConnection.getAuthPassword();
        final SslClientCertificate sslCert = clientConnection.getAuthCertificate();
        final Listener listener = clientConnection.getConnectedListener();
        final Optional<Long> disconnectTimestampOptional = Optional.ofNullable(disconnectTimestamp);

        final ClientToken clientToken = new ClientToken(clientId,
                username,
                password,
                sslCert,
                false,
                getChannelAddress(channel).orElse(null),
                listener,
                disconnectTimestampOptional);

        final ClientState clientState = clientConnection.getClientState();
        // Clients are seen as authenticated in both states.
        final boolean authenticatedOrAuthenticating =
                (clientState == ClientState.AUTHENTICATED) || (clientState == ClientState.RE_AUTHENTICATING);

        clientToken.setAuthenticated(authenticatedOrAuthenticating);

        return clientToken;
    }

}
