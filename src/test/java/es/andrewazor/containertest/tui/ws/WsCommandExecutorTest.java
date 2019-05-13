package es.andrewazor.containertest.tui.ws;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.stream.MalformedJsonException;

import org.hamcrest.MatcherAssert;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.stubbing.Answer;

import es.andrewazor.containertest.commands.SerializableCommand.ExceptionOutput;
import es.andrewazor.containertest.commands.SerializableCommand.FailureOutput;
import es.andrewazor.containertest.commands.SerializableCommand.ListOutput;
import es.andrewazor.containertest.commands.SerializableCommand.MapOutput;
import es.andrewazor.containertest.commands.SerializableCommand.Output;
import es.andrewazor.containertest.commands.SerializableCommand.StringOutput;
import es.andrewazor.containertest.commands.SerializableCommand.SuccessOutput;
import es.andrewazor.containertest.commands.SerializableCommandRegistry;
import es.andrewazor.containertest.tui.ClientReader;
import es.andrewazor.containertest.tui.ClientWriter;

@ExtendWith(MockitoExtension.class)
class WsCommandExecutorTest {

    WsCommandExecutor executor;
    @Mock MessagingServer server;
    @Mock WsClientReaderWriter connection;
    @Mock ClientReader cr;
    @Mock ClientWriter cw;
    @Mock SerializableCommandRegistry commandRegistry;
    Gson gson;

    @BeforeEach
    void setup() {
        gson = new GsonBuilder().serializeNulls().create();
        executor = new WsCommandExecutor(server, cr, cw, () -> commandRegistry, gson);
    }

    @Test
    void shouldExecuteWellFormedValidCommand() throws Exception {
        when(cr.readLine()).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                executor.shutdown();
                return "{\"command\":\"help\",\"args\":[]}";
            }
        });
        when(server.getConnection()).thenReturn(connection);
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("help"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(true);
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(new SuccessOutput());

        executor.run(null);

        InOrder inOrder = inOrder(commandRegistry, connection);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("help");
        inOrder.verify(commandRegistry).validate("help", new String[0]);
        inOrder.verify(commandRegistry).execute("help", new String[0]);
        inOrder.verify(connection).flush(Mockito.any(SuccessResponseMessage.class));
    }

    @Test
    void shouldExecuteWellFormedValidCommandWithArgs() throws Exception {
        when(cr.readLine()).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                executor.shutdown();
                return "{\"command\":\"help\",\"args\":[\"hello\",\"world\"]}";
            }
        });
        when(server.getConnection()).thenReturn(connection);
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("help"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(true);
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(new SuccessOutput());

        executor.run(null);

        InOrder inOrder = inOrder(commandRegistry, connection);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("help");
        inOrder.verify(commandRegistry).validate("help", new String[]{ "hello", "world" });
        inOrder.verify(commandRegistry).execute("help", new String[]{ "hello", "world" });
        inOrder.verify(connection).flush(Mockito.any(SuccessResponseMessage.class));
    }

    @Test
    void shouldHandleFailureOutputs() throws Exception {
        when(cr.readLine()).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                executor.shutdown();
                return "{\"command\":\"help\",\"args\":[\"hello\",\"world\"]}";
            }
        });
        when(server.getConnection()).thenReturn(connection);
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("help"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(true);
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(new FailureOutput("some reason"));

        executor.run(null);

        ArgumentCaptor<FailureResponseMessage> response = ArgumentCaptor.forClass(FailureResponseMessage.class);
        InOrder inOrder = inOrder(commandRegistry, connection);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("help");
        inOrder.verify(commandRegistry).validate("help", new String[]{ "hello", "world" });
        inOrder.verify(commandRegistry).execute("help", new String[]{ "hello", "world" });
        inOrder.verify(connection).flush(response.capture());

        MatcherAssert.assertThat(response.getValue().status, Matchers.equalTo(-1));
        MatcherAssert.assertThat(response.getValue().commandName, Matchers.equalTo("help"));
        MatcherAssert.assertThat(response.getValue().payload, Matchers.equalTo("some reason"));
    }

    @Test
    void shouldHandleStringOutputs() throws Exception {
        when(cr.readLine()).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                executor.shutdown();
                return "{\"command\":\"help\",\"args\":[\"hello\",\"world\"]}";
            }
        });
        when(server.getConnection()).thenReturn(connection);
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("help"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(true);
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(new StringOutput("some reason"));

        executor.run(null);

        ArgumentCaptor<SuccessResponseMessage<String>> response = ArgumentCaptor.forClass(SuccessResponseMessage.class);
        InOrder inOrder = inOrder(commandRegistry, connection);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("help");
        inOrder.verify(commandRegistry).validate("help", new String[]{ "hello", "world" });
        inOrder.verify(commandRegistry).execute("help", new String[]{ "hello", "world" });
        inOrder.verify(connection).flush(response.capture());

        MatcherAssert.assertThat(response.getValue().status, Matchers.equalTo(0));
        MatcherAssert.assertThat(response.getValue().commandName, Matchers.equalTo("help"));
        MatcherAssert.assertThat(response.getValue().payload, Matchers.equalTo("some reason"));
    }

    @Test
    void shouldHandleListOutputs() throws Exception {
        when(cr.readLine()).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                executor.shutdown();
                return "{\"command\":\"help\",\"args\":[\"hello\",\"world\"]}";
            }
        });
        when(server.getConnection()).thenReturn(connection);
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("help"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(true);
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(new ListOutput<Integer>(Arrays.asList(3, 1, 4, 1, 5, 9)));

        executor.run(null);

        ArgumentCaptor<SuccessResponseMessage<List<Integer>>> response = ArgumentCaptor.forClass(SuccessResponseMessage.class);
        InOrder inOrder = inOrder(commandRegistry, connection);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("help");
        inOrder.verify(commandRegistry).validate("help", new String[]{ "hello", "world" });
        inOrder.verify(commandRegistry).execute("help", new String[]{ "hello", "world" });
        inOrder.verify(connection).flush(response.capture());

        MatcherAssert.assertThat(response.getValue().status, Matchers.equalTo(0));
        MatcherAssert.assertThat(response.getValue().commandName, Matchers.equalTo("help"));
        MatcherAssert.assertThat(response.getValue().payload, Matchers.equalTo(Arrays.asList(3, 1, 4, 1, 5, 9)));
    }

    @Test
    void shouldHandleMapOutputs() throws Exception {
        when(cr.readLine()).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                executor.shutdown();
                return "{\"command\":\"help\",\"args\":[\"hello\",\"world\"]}";
            }
        });
        when(server.getConnection()).thenReturn(connection);
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("help"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(true);
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(new MapOutput<String, String>(Map.of("foo", "bar")));

        executor.run(null);

        ArgumentCaptor<SuccessResponseMessage<Map<String, String>>> response = ArgumentCaptor.forClass(SuccessResponseMessage.class);
        InOrder inOrder = inOrder(commandRegistry, connection);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("help");
        inOrder.verify(commandRegistry).validate("help", new String[]{ "hello", "world" });
        inOrder.verify(commandRegistry).execute("help", new String[]{ "hello", "world" });
        inOrder.verify(connection).flush(response.capture());

        MatcherAssert.assertThat(response.getValue().status, Matchers.equalTo(0));
        MatcherAssert.assertThat(response.getValue().commandName, Matchers.equalTo("help"));
        MatcherAssert.assertThat(response.getValue().payload, Matchers.equalTo(Map.of("foo", "bar")));
    }

    @Test
    void shouldHandleExceptionOutputs() throws Exception {
        when(cr.readLine()).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                executor.shutdown();
                return "{\"command\":\"help\",\"args\":[\"hello\",\"world\"]}";
            }
        });
        when(server.getConnection()).thenReturn(connection);
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("help"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(true);
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(new ExceptionOutput(new IOException("broken pipe")));

        executor.run(null);

        ArgumentCaptor<CommandExceptionResponseMessage> response = ArgumentCaptor.forClass(CommandExceptionResponseMessage.class);
        InOrder inOrder = inOrder(commandRegistry, connection);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("help");
        inOrder.verify(commandRegistry).validate("help", new String[]{ "hello", "world" });
        inOrder.verify(commandRegistry).execute("help", new String[]{ "hello", "world" });
        inOrder.verify(connection).flush(response.capture());

        MatcherAssert.assertThat(response.getValue().status, Matchers.equalTo(-2));
        MatcherAssert.assertThat(response.getValue().commandName, Matchers.equalTo("help"));
        MatcherAssert.assertThat(response.getValue().payload, Matchers.equalTo("IOException: broken pipe"));
    }

    @Test
    void shouldHandleUnknownOutputs() throws Exception {
        when(cr.readLine()).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                executor.shutdown();
                return "{\"command\":\"help\",\"args\":[\"hello\",\"world\"]}";
            }
        });
        when(server.getConnection()).thenReturn(connection);
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("help"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(true);
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(new Output() {
            @Override
            public boolean success() {
                return true;
            }
        });

        executor.run(null);

        ArgumentCaptor<CommandExceptionResponseMessage> response = ArgumentCaptor.forClass(CommandExceptionResponseMessage.class);
        InOrder inOrder = inOrder(commandRegistry, connection);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("help");
        inOrder.verify(commandRegistry).validate("help", new String[]{ "hello", "world" });
        inOrder.verify(commandRegistry).execute("help", new String[]{ "hello", "world" });
        inOrder.verify(connection).flush(response.capture());

        MatcherAssert.assertThat(response.getValue().status, Matchers.equalTo(-2));
        MatcherAssert.assertThat(response.getValue().commandName, Matchers.equalTo("help"));
        MatcherAssert.assertThat(response.getValue().payload, Matchers.equalTo("internal error"));
    }

    @Test
    void shouldSkipNullLines() throws Exception {
        when(cr.readLine()).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                executor.shutdown();
                return null;
            }
        });

        executor.run(null);

        verifyZeroInteractions(commandRegistry);
        verifyZeroInteractions(server);
        verifyZeroInteractions(cw);
    }

    @Test
    void shouldInterpretMissingArgsAsEmpty() throws Exception {
        when(cr.readLine()).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                executor.shutdown();
                return "{\"command\":\"help\"}";
            }
        });
        when(server.getConnection()).thenReturn(connection);
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("help"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(true);
        when(commandRegistry.execute(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(new SuccessOutput());

        executor.run(null);

        InOrder inOrder = inOrder(commandRegistry, connection);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("help");
        inOrder.verify(commandRegistry).validate("help", new String[0]);
        inOrder.verify(commandRegistry).execute("help", new String[0]);
        inOrder.verify(connection).flush(Mockito.any(SuccessResponseMessage.class));
    }

    @Test
    void shouldRespondToUnregisteredCommand() throws Exception {
        when(cr.readLine()).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                executor.shutdown();
                return "{\"command\":\"foo\"}";
            }
        });
        when(server.getConnection()).thenReturn(connection);
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("bar"));

        executor.run(null);

        verify(commandRegistry).getRegisteredCommandNames();
        verifyNoMoreInteractions(commandRegistry);

        ArgumentCaptor<ResponseMessage<String>> messageCaptor = ArgumentCaptor.forClass(ResponseMessage.class);
        verify(connection).flush(messageCaptor.capture());
        ResponseMessage<String> message = messageCaptor.getValue();
        MatcherAssert.assertThat(message.status, Matchers.equalTo(-1));
    }

    @Test
    void shouldRespondToInvalidCommand() throws Exception {
        when(cr.readLine()).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                executor.shutdown();
                return "{\"command\":\"foo\"}";
            }
        });
        when(server.getConnection()).thenReturn(connection);
        when(commandRegistry.validate(Mockito.anyString(), Mockito.any(String[].class))).thenReturn(false);
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("foo"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(true);

        executor.run(null);

        InOrder inOrder = inOrder(commandRegistry);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("foo");
        inOrder.verify(commandRegistry).validate("foo", new String[0]);
        verifyNoMoreInteractions(commandRegistry);

        ArgumentCaptor<ResponseMessage<String>> messageCaptor = ArgumentCaptor.forClass(ResponseMessage.class);
        verify(connection).flush(messageCaptor.capture());
        ResponseMessage<String> message = messageCaptor.getValue();
        MatcherAssert.assertThat(message.status, Matchers.equalTo(-1));
    }

    @Test
    void shouldRespondToUnavailableCommand() throws Exception {
        when(cr.readLine()).thenAnswer(new Answer<String>() {
            @Override
            public String answer(InvocationOnMock invocation) throws Throwable {
                executor.shutdown();
                return "{\"command\":\"foo\"}";
            }
        });
        when(server.getConnection()).thenReturn(connection);
        when(commandRegistry.getRegisteredCommandNames()).thenReturn(Collections.singleton("foo"));
        when(commandRegistry.isCommandAvailable(Mockito.anyString())).thenReturn(false);

        executor.run(null);

        InOrder inOrder = inOrder(commandRegistry);
        inOrder.verify(commandRegistry).getRegisteredCommandNames();
        inOrder.verify(commandRegistry).isCommandAvailable("foo");
        verifyNoMoreInteractions(commandRegistry);

        ArgumentCaptor<ResponseMessage<String>> messageCaptor = ArgumentCaptor.forClass(ResponseMessage.class);
        verify(connection).flush(messageCaptor.capture());
        ResponseMessage<String> message = messageCaptor.getValue();
        MatcherAssert.assertThat(message.status, Matchers.equalTo(-1));
    }

    @Test
    void shouldReportInvalidJSONExceptions() throws Exception {
        PrintStream origErr = System.err;
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            System.setErr(new PrintStream(baos));

            when(cr.readLine()).thenAnswer(new Answer<String>() {
                @Override
                public String answer(InvocationOnMock invocation) throws Throwable {
                    executor.shutdown();
                    return "{\"command\":\"help}";
                }
            });
            when(server.getConnection()).thenReturn(connection);

            executor.run(null);

            ArgumentCaptor<CommandExceptionResponseMessage> messageCaptor = ArgumentCaptor.forClass(CommandExceptionResponseMessage.class);
            verify(connection).flush(messageCaptor.capture());
            ResponseMessage<String> message = messageCaptor.getValue();
            MatcherAssert.assertThat(message.status, Matchers.equalTo(-2));

            verifyZeroInteractions(commandRegistry);

            MatcherAssert.assertThat(baos.toString(), Matchers.stringContainsInOrder(
                    JsonSyntaxException.class.getName(), MalformedJsonException.class.getName()));
        } finally {
            System.setErr(origErr);
        }
    }

}