import { useEffect, useRef, useState } from 'react';
import { Client, IMessage } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { usePollStore } from '../store/pollStore';

const WS_URL = import.meta.env.VITE_WS_URL || '/ws';
const WS_TIMEOUT_MS = 8_000;

export type ConnectionType = 'websocket' | 'sse' | 'none';

export const useRealtimeVotes = (pollId: string | null) => {
  const [isConnected, setIsConnected]       = useState(false);
  const [connectionType, setConnectionType] = useState<ConnectionType>('none');

  const { updateVoteCounts } = usePollStore();

  const stompRef    = useRef<Client | null>(null);
  const sseRef      = useRef<EventSource | null>(null);
  const wsTimerRef  = useRef<ReturnType<typeof setTimeout> | null>(null);
  const wsConnected = useRef(false);

  useEffect(() => {
    if (!pollId) return;

    const handleMessage = (raw: string) => {
      try {
        const counts = JSON.parse(raw) as Record<string, number>;
        updateVoteCounts(pollId, counts);
      } catch {
        // ignore malformed frames
      }
    };

    const connectSse = () => {
      const sse = new EventSource(`/api/polls/${pollId}/stream`);
      sseRef.current = sse;

      sse.onopen = () => {
        setIsConnected(true);
        setConnectionType('sse');
      };
      sse.onmessage = (e) => handleMessage(e.data);
      sse.onerror   = () => {
        setIsConnected(false);
        setConnectionType('none');
      };
    };

    // Fall back to SSE if WebSocket hasn't connected within the timeout
    wsTimerRef.current = setTimeout(() => {
      if (!wsConnected.current) {
        stompRef.current?.deactivate();
        connectSse();
      }
    }, WS_TIMEOUT_MS);

    const client = new Client({
      webSocketFactory: () => new SockJS(WS_URL),
      reconnectDelay: 5_000,
      onConnect: () => {
        if (wsTimerRef.current) {
          clearTimeout(wsTimerRef.current);
          wsTimerRef.current = null;
        }
        wsConnected.current = true;
        setIsConnected(true);
        setConnectionType('websocket');

        client.subscribe(`/topic/polls/${pollId}/votes`, (msg: IMessage) => {
          handleMessage(msg.body);
        });
      },
      onDisconnect: () => {
        wsConnected.current = false;
        setIsConnected(false);
        setConnectionType('none');
      },
      onStompError: () => {
        // timeout will fire SSE fallback if WS never connected
      },
    });

    client.activate();
    stompRef.current = client;

    return () => {
      if (wsTimerRef.current) clearTimeout(wsTimerRef.current);
      client.deactivate();
      stompRef.current    = null;
      wsConnected.current = false;
      sseRef.current?.close();
      sseRef.current = null;
      setIsConnected(false);
      setConnectionType('none');
    };
  }, [pollId, updateVoteCounts]);

  return { isConnected, connectionType };
};
