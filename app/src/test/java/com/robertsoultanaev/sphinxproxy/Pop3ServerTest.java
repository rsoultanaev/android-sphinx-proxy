package com.robertsoultanaev.sphinxproxy;

import com.robertsoultanaev.sphinxproxy.database.AssembledMessage;
import com.robertsoultanaev.sphinxproxy.database.DBQuery;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.*;
import static org.mockito.Mockito.*;

import org.apache.commons.io.IOUtils;
import org.apache.commons.net.pop3.POP3Client;
import org.apache.commons.net.pop3.POP3MessageInfo;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.BufferedReader;
import java.util.ArrayList;
import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class Pop3ServerTest {

    @Mock
    private DBQuery dbQuery;

    @Test
    public void normalSessionTest() throws Exception {
        String msg1Body = "hello" + Pop3Server.CRLF;
        String msg2Body = "bye" + Pop3Server.CRLF;
        AssembledMessage msg1 = new AssembledMessage("1", msg1Body.getBytes());
        AssembledMessage msg2 = new AssembledMessage("2", msg2Body.getBytes());

        List<AssembledMessage> msgList = new ArrayList<AssembledMessage>();
        msgList.add(msg1);
        msgList.add(msg2);

        int pop3Port = 27000;
        String username = "proxyuser";
        String password = "12345";
        String host = "localhost";

        doNothing().when(dbQuery).deleteAssembledMessage(anyString());
        when(dbQuery.getAssembledMessages()).thenReturn(msgList);

        Pop3Server pop3Server = new Pop3Server(pop3Port, username, password, dbQuery);
        pop3Server.start();
        Thread.sleep(1000);

        POP3Client pop3Client = new POP3Client();

        pop3Client.connect(host, pop3Port);

        // USER & PASS
        pop3Client.login(username, password);

        // STAT
        POP3MessageInfo status = pop3Client.status();

        int expectedNumber = msgList.size();
        int expectedSize = 0;
        for (AssembledMessage msg : msgList) {
            expectedSize += msg.messageBody.length;
        }

        assertThat(status.number, is(equalTo(expectedNumber)));
        assertThat(status.size, is(equalTo(expectedSize)));

        // LIST
        POP3MessageInfo[] msgInfo = pop3Client.listMessages();

        expectedNumber = msgList.size();

        assertThat(msgInfo.length, is(equalTo(expectedNumber)));
        for (int i = 0; i < msgInfo.length; i++) {
            assertThat(msgInfo[i].number, is(equalTo(i + 1)));
            assertThat(msgInfo[i].size, is(equalTo(msgList.get(i).messageBody.length)));
        }

        // UIDL
        msgInfo = pop3Client.listUniqueIdentifiers();

        expectedNumber = msgList.size();

        assertThat(msgInfo.length, is(equalTo(expectedNumber)));
        for (int i = 0; i < msgInfo.length; i++) {
            assertThat(msgInfo[i].number, is(equalTo(i + 1)));
            assertThat(msgInfo[i].identifier, is(equalTo(msgList.get(i).uuid)));
        }

        // RETR
        List<String> retrievedMessages = new ArrayList<String>();
        for (int i = 1; i <= msgList.size(); i++) {
            BufferedReader reader = (BufferedReader) pop3Client.retrieveMessage(i);
            retrievedMessages.add(IOUtils.toString(reader));
        }

        assertThat(retrievedMessages.size(), is(equalTo(msgList.size())));
        for (int i = 0; i < retrievedMessages.size(); i++) {
            String expectedMsg = new String(msgList.get(i).messageBody);
            assertThat(retrievedMessages.get(i), is(equalTo(expectedMsg)));
        }

        pop3Client.logout();
        pop3Server.stop();
    }

    @Test
    public void multipleSessionsTest() throws Exception {
        List<AssembledMessage> msgList = new ArrayList<AssembledMessage>();

        int pop3Port = 27000;
        String username = "proxyuser";
        String password = "12345";
        String host = "localhost";

        doNothing().when(dbQuery).deleteAssembledMessage(anyString());
        when(dbQuery.getAssembledMessages()).thenReturn(msgList);

        Pop3Server pop3Server = new Pop3Server(pop3Port, username, password, dbQuery);
        pop3Server.start();
        Thread.sleep(1000);

        POP3Client pop3Client = new POP3Client();

        pop3Client.connect(host, pop3Port);
        pop3Client.login(username, password);
        pop3Client.status();
        pop3Client.logout();

        pop3Client.connect(host, pop3Port);
        pop3Client.login(username, password);
        pop3Client.status();
        pop3Client.logout();

        pop3Client.connect(host, pop3Port);
        pop3Client.login(username, password);
        pop3Client.status();
        pop3Client.logout();

        pop3Server.stop();
    }
}
