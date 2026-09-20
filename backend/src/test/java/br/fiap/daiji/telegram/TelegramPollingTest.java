package br.fiap.daiji.telegram;

import br.fiap.daiji.assistant.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TelegramPollingTest {
    TelegramUpdateSource source = mock(TelegramUpdateSource.class);
    TelegramUpdateReceiver receiver = mock(TelegramUpdateReceiver.class);
    List<Long> waits = new ArrayList<>();
    TelegramPollingService polling = new TelegramPollingService(source,receiver,25,true,waits::add);
    TelegramUpdate update(long id) { return new TelegramUpdate(id,null); }

    @Test void offsetOrdenacaoDuplicadosEIgnorados() throws Exception {
        when(source.getUpdates(0,25)).thenReturn(List.of(update(5),update(3),update(3),update(4)));
        when(source.getUpdates(6,25)).thenReturn(List.of(update(3),update(5),update(6)));
        polling.pollOnce(); polling.pollOnce();
        var order = inOrder(receiver);
        order.verify(receiver).receive(update(3)); order.verify(receiver).receive(update(4));
        order.verify(receiver).receive(update(5)); order.verify(receiver).receive(update(6));
        verifyNoMoreInteractions(receiver); verify(source).getUpdates(6,25);
    }
    @Test void falhaMensagemNaoMataLoteNemRepete() throws Exception {
        when(source.getUpdates(0,25)).thenReturn(List.of(update(1),update(2)));
        when(source.getUpdates(3,25)).thenReturn(List.of(update(1)));
        doThrow(new IllegalStateException("interno")).when(receiver).receive(update(1));
        polling.pollOnce(); polling.pollOnce();
        verify(receiver).receive(update(1)); verify(receiver).receive(update(2));
        assertEquals(List.of(1000L,250L,250L),waits);
    }
    @Test void backoffExponencialLimitadoEReset() throws Exception {
        when(source.getUpdates(0,25)).thenThrow(new TelegramApiException(503,0));
        for (int i=0;i<7;i++) polling.pollOnce();
        assertEquals(List.of(1000L,2000L,4000L,8000L,16000L,30000L,30000L), waits);
        doReturn(List.of()).when(source).getUpdates(0,25); polling.pollOnce();
        when(source.getUpdates(0,25)).thenThrow(new TelegramApiException(503,0)); polling.pollOnce();
        assertEquals(1000L,waits.getLast());
    }
    @Test void rateLimitRespeitado() throws Exception {
        when(source.getUpdates(0,25)).thenThrow(new TelegramApiException(429,45));
        polling.pollOnce(); assertEquals(List.of(45000L),waits);
    }
    @Test void rateLimitEnvioRespeitado() throws Exception {
        when(source.getUpdates(0,25)).thenReturn(List.of(update(1)));
        doThrow(new TelegramApiException(429,7)).when(receiver).receive(update(1));
        polling.pollOnce(); assertEquals(List.of(7000L,250L),waits);
    }
    @Test void vazioNaoBusyLoop() throws Exception {
        when(source.getUpdates(0,25)).thenReturn(List.of()); polling.pollOnce();
        assertEquals(List.of(250L),waits); verifyNoInteractions(receiver);
    }
    @Test void idsAusentesInvalidosIgnorados() throws Exception {
        when(source.getUpdates(0,25)).thenReturn(Arrays.asList(null, new TelegramUpdate(null,null), update(-1), update(Long.MAX_VALUE), update(1)));
        polling.pollOnce(); verify(receiver).receive(update(1)); verifyNoMoreInteractions(receiver);
    }
    @Test void naoIniciaSemConfiguracao() {
        var disabled = new TelegramPollingService(source,receiver,25,false,waits::add);
        disabled.start(); assertFalse(disabled.isRunning()); disabled.stop(); verifyNoInteractions(source);
    }
    @Test void startNaoBloqueiaNaoDuplicaWorkerEShutdownInterrompe() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), exited = new CountDownLatch(1);
        when(source.getUpdates(0,25)).thenAnswer(call -> {
            entered.countDown();
            try { new CountDownLatch(1).await(); return List.of(); }
            finally { exited.countDown(); }
        });
        try {
            polling.start(); polling.start(); assertTrue(entered.await(2,TimeUnit.SECONDS)); assertTrue(polling.isRunning());
        } finally { polling.stop(); }
        assertTrue(exited.await(2,TimeUnit.SECONDS)); assertFalse(polling.isRunning());
        verify(source).getUpdates(0,25);
    }
    @Test void shutdownInterrompeBackoff() throws Exception {
        CountDownLatch entered = new CountDownLatch(1), exited = new CountDownLatch(1);
        when(source.getUpdates(0,25)).thenThrow(new TelegramApiException(503,0));
        var service = new TelegramPollingService(source,receiver,25,true,millis -> {
            entered.countDown(); try { new CountDownLatch(1).await(); } finally { exited.countDown(); }
        });
        try { service.start(); assertTrue(entered.await(2,TimeUnit.SECONDS)); }
        finally { service.stop(); }
        assertTrue(exited.await(2,TimeUnit.SECONDS)); assertFalse(service.isRunning());
    }
    @ParameterizedTest @ValueSource(ints = {401,404})
    void credencialRecusadaDesativa(int status) throws Exception {
        when(source.getUpdates(0,25)).thenThrow(new TelegramApiException(status,0));
        polling.pollOnce(); assertFalse(polling.isRunning()); assertTrue(waits.isEmpty());
    }
    @Test void receiverSoAceitaTextoPrivado() throws Exception {
        MessageRouter router = mock(MessageRouter.class); TelegramClient client = mock(TelegramClient.class);
        var actual = new TelegramUpdateReceiver(router,new ResponseFormatter(),client);
        actual.receive(null); actual.receive(update(1));
        actual.receive(new TelegramUpdate(2L,new TelegramUpdate.Message(null,"Oi")));
        actual.receive(new TelegramUpdate(3L,new TelegramUpdate.Message(new TelegramUpdate.Chat(null,"private"),"Oi")));
        actual.receive(new TelegramUpdate(4L,new TelegramUpdate.Message(new TelegramUpdate.Chat(1L,"private"),null)));
        for (String type : List.of("group","supergroup","channel",""))
            actual.receive(new TelegramUpdate(5L,new TelegramUpdate.Message(new TelegramUpdate.Chat(1L,type),"Oi")));
        verifyNoInteractions(router,client);
        when(router.route("/start")).thenReturn(new AssistantResponse.Text("Olá!"));
        actual.receive(new TelegramUpdate(6L,new TelegramUpdate.Message(new TelegramUpdate.Chat(9000000000L,"private"),"/start")));
        verify(router).route("/start"); verify(client).sendMessage(9000000000L,"Olá!");
    }
}
