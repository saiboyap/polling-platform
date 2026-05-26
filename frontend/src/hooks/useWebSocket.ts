import { useEffect, useRef } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { usePollStore } from '../store/pollStore';

const WS_URL = import.meta.env.VITE_WS_URL || '/ws';

export const useWebSocket = (pollId: string | null) => {
  const clientRef = useRef<Client | null>(null);
  const { updateVoteCounts } = usePollStore();

  useEffect(() => {
    if (!pollId) return;

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/polls/${pollId}/votes`, (message: IMessage) => {
          try {
            const counts = JSON.parse(message.body) as Record<string, number>;
            updateVoteCounts(pollId, counts);
          } catch {
            // ignore malformed messages
          }
        });
      },
      onStompError: (frame) => {
        console.error('STOMP error', frame);
      },
    });

    client.activate();
    clientRef.current = client;

    return () => {
      client.deactivate();
      clientRef.current = null;
    };
  }, [pollId, updateVoteCounts]);
};
