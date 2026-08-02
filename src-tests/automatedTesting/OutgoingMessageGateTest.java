package automatedTesting;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import net.xqhs.flash.core.agent.AgentWave;
import net.xqhs.flash.core.shard.AgentShardDesignation;
import net.xqhs.flash.core.support.AbstractMessagingShard;
import net.xqhs.flash.core.support.ClassicMessageReceiver;
import net.xqhs.flash.core.support.ClassicMessagingPylonProxy;
import net.xqhs.flash.core.support.MessageDecision;
import net.xqhs.flash.core.support.OutgoingMessageContext;
import net.xqhs.flash.core.support.WaveMessagingPylonProxy;
import net.xqhs.flash.core.support.WaveReceiver;

public class OutgoingMessageGateTest {
	private static class StubMessagingShard extends AbstractMessagingShard {
		private static final long serialVersionUID = 1L;

		@Override
		public String extractAgentAddress(String endpoint) {
			return endpoint.split(AgentWave.ADDRESS_SEPARATOR, 2)[0];
		}

		@Override
		public String getAgentAddress() {
			return "source-agent";
		}
	}

	private static class RecordingWavePylon implements WaveMessagingPylonProxy {
		private int sends;

		@Override
		public String getRecommendedShardImplementation(AgentShardDesignation shardType) {
			return null;
		}

		@Override
		public String getEntityName() {
			return "test-pylon";
		}

		@Override
		public boolean register(String entityName, WaveReceiver receiver) {
			return true;
		}

		@Override
		public boolean unregister(String entityName, WaveReceiver registeredReceiver) {
			return true;
		}

		@Override
		public boolean send(AgentWave wave) {
			sends++;
			return true;
		}
	}

	private static class RecordingClassicPylon implements ClassicMessagingPylonProxy {
		private int sends;

		@Override
		public String getRecommendedShardImplementation(AgentShardDesignation shardType) {
			return null;
		}

		@Override
		public String getEntityName() {
			return "classic-test-pylon";
		}

		@Override
		public boolean register(String entityName, ClassicMessageReceiver receiver) {
			return true;
		}

		@Override
		public boolean unregister(String entityName, ClassicMessageReceiver registeredReceiver) {
			return true;
		}

		@Override
		public boolean send(String source, String destination, String content) {
			sends++;
			return true;
		}
	}

	private StubMessagingShard shardWith(RecordingWavePylon pylon) {
		StubMessagingShard shard = new StubMessagingShard();
		assertTrue(shard.addGeneralContext(pylon));
		return shard;
	}

	@Test
	public void denialPreventsHookAndPylonDispatch() {
		RecordingWavePylon pylon = new RecordingWavePylon();
		StubMessagingShard shard = shardWith(pylon);
		AtomicInteger hookCalls = new AtomicInteger();
		AtomicReference<OutgoingMessageContext> inspected = new AtomicReference<>();

		shard.addOutgoingMessageGate(context -> {
			inspected.set(context);
			return MessageDecision.deny("scope-mismatch", "policy:v7", "evidence:17");
		});
		shard.addOutgoingMessageHook(new net.xqhs.flash.core.support.MessagingShard.OutgoingMessageHook() {
			@Override
			public void sendingMessage(String source, String destination, String content) {
				hookCalls.incrementAndGet();
			}

			@Override
			public void sendingMessage(AgentWave wave) {
				hookCalls.incrementAndGet();
			}
		});

		boolean sent = shard.sendMessage(new AgentWave("payload", "target-agent", "endpoint"));

		assertFalse(sent);
		assertEquals(0, pylon.sends);
		assertEquals(0, hookCalls.get());
		assertEquals("source-agent", inspected.get().getSource());
		assertEquals("target-agent/endpoint", inspected.get().getDestination());
		assertEquals("payload", inspected.get().getSerializedContent());
	}

	@Test
	public void permitPreservesExistingDispatchPath() {
		RecordingWavePylon pylon = new RecordingWavePylon();
		StubMessagingShard shard = shardWith(pylon);
		AtomicInteger hookCalls = new AtomicInteger();
		shard.addOutgoingMessageGate(
				context -> MessageDecision.permit("policy:v7", "evidence:18"));
		shard.addOutgoingMessageHook(new net.xqhs.flash.core.support.MessagingShard.OutgoingMessageHook() {
			@Override
			public void sendingMessage(String source, String destination, String content) {
				hookCalls.incrementAndGet();
			}

			@Override
			public void sendingMessage(AgentWave wave) {
				hookCalls.incrementAndGet();
			}
		});

		assertTrue(shard.sendMessage(new AgentWave("payload", "target-agent")));
		assertEquals(1, pylon.sends);
		assertEquals(1, hookCalls.get());
	}

	@Test
	public void gateFailureFailsClosed() {
		RecordingWavePylon pylon = new RecordingWavePylon();
		StubMessagingShard shard = shardWith(pylon);
		shard.addOutgoingMessageGate(context -> {
			throw new IllegalStateException("policy engine unavailable");
		});

		assertFalse(shard.sendMessage(new AgentWave("payload", "target-agent")));
		assertEquals(0, pylon.sends);
	}

	@Test
	public void hookMutationAfterPermitFailsClosed() {
		RecordingWavePylon pylon = new RecordingWavePylon();
		StubMessagingShard shard = shardWith(pylon);
		shard.addOutgoingMessageGate(
				context -> MessageDecision.permit("policy:v7", "evidence:19"));
		shard.addOutgoingMessageHook(new net.xqhs.flash.core.support.MessagingShard.OutgoingMessageHook() {
			@Override
			public void sendingMessage(String source, String destination, String content) {
				// The wave overload is used in this test.
			}

			@Override
			public void sendingMessage(AgentWave wave) {
				wave.add(AgentWave.CONTENT, "substituted-after-permit");
			}
		});

		assertFalse(shard.sendMessage(new AgentWave("payload", "target-agent")));
		assertEquals(0, pylon.sends);
	}

	@Test
	public void classicDispatchIsAlsoGated() {
		RecordingClassicPylon pylon = new RecordingClassicPylon();
		StubMessagingShard shard = new StubMessagingShard();
		assertTrue(shard.addGeneralContext(pylon));
		AtomicReference<OutgoingMessageContext> inspected = new AtomicReference<>();
		shard.addOutgoingMessageGate(context -> {
			inspected.set(context);
			return MessageDecision.deny("classic-denial", "policy:v7", "evidence:20");
		});

		assertFalse(shard.sendMessage("source-agent/endpoint", "target-agent/endpoint", "payload"));
		assertEquals(0, pylon.sends);
		assertEquals("source-agent/endpoint", inspected.get().getSource());
		assertEquals("target-agent/endpoint", inspected.get().getDestination());
	}
}
