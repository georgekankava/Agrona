/*
 * Copyright 2014 - 2016 Real Logic Ltd.
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
package uk.co.real_logic.agrona.concurrent.broadcast;

import uk.co.real_logic.agrona.MutableDirectBuffer;
import uk.co.real_logic.agrona.concurrent.MessageHandler;
import uk.co.real_logic.agrona.concurrent.UnsafeBuffer;

/**
 * Receiver that copies messages that have been broadcast to enable a simpler API for the client.
 */
public class CopyBroadcastReceiver
{
    private static final int SCRATCH_BUFFER_SIZE = 4096;

    private final BroadcastReceiver receiver;
    private final MutableDirectBuffer scratchBuffer;

    /**
     * Wrap a {@link BroadcastReceiver} to simplify the API for receiving messages.
     *
     * @param receiver to be wrapped.
     */
    public CopyBroadcastReceiver(final BroadcastReceiver receiver)
    {
        this.receiver = receiver;
        scratchBuffer = new UnsafeBuffer(new byte[SCRATCH_BUFFER_SIZE]);

        while (receiver.receiveNext())
        {
            // If we're reconnecting to a broadcast buffer then we need to
            // scan ourselves up to date, otherwise we risk "falling behind"
            // the buffer due to the time taken to catchup.
        }
    }

    /**
     * Receive one message from the broadcast buffer.
     *
     * @param handler to be called for each message received.
     * @return the number of messages that have been received.
     */
    public int receive(final MessageHandler handler)
    {
        int messagesReceived = 0;
        final BroadcastReceiver receiver = this.receiver;
        final long lastSeenLappedCount = receiver.lappedCount();

        if (receiver.receiveNext())
        {
            if (lastSeenLappedCount != receiver.lappedCount())
            {
                throw new IllegalStateException("Unable to keep up with broadcast buffer");
            }

            final int length = receiver.length();
            final int capacity = scratchBuffer.capacity();
            if (length > capacity)
            {
                throw new IllegalStateException(String.format("Buffer required size %d but only has %d", length, capacity));
            }

            final int msgTypeId = receiver.typeId();
            scratchBuffer.putBytes(0, receiver.buffer(), receiver.offset(), length);

            if (!receiver.validate())
            {
                throw new IllegalStateException("Unable to keep up with broadcast buffer");
            }

            handler.onMessage(msgTypeId, scratchBuffer, 0, length);

            messagesReceived = 1;
        }

        return messagesReceived;
    }
}
