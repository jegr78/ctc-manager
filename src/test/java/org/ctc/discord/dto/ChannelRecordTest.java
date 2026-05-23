package org.ctc.discord.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class ChannelRecordTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	void given4ArgConstructor_whenConstructed_thenPermissionOverwritesAndMetadataNull() {
		Channel channel = new Channel("id1", "name1", 0, "parent1");

		assertThat(channel.permissionOverwrites()).isNull();
		assertThat(channel.threadMetadata()).isNull();
	}

	@Test
	void given5ArgConstructor_whenConstructed_thenThreadMetadataNull() {
		Channel channel = new Channel("id2", "name2", 0, "parent2", java.util.List.of());

		assertThat(channel.permissionOverwrites()).isEmpty();
		assertThat(channel.threadMetadata()).isNull();
	}

	@Test
	void givenChannelJsonWithThreadMetadata_whenDeserialized_thenThreadMetadataReflectsArchived() throws Exception {
		String json = """
				{"id":"t1","name":"Thread","type":11,"parent_id":"forum-1",
				 "thread_metadata":{"archived":true}}
				""";

		Channel channel = MAPPER.readValue(json, Channel.class);

		assertThat(channel.id()).isEqualTo("t1");
		assertThat(channel.threadMetadata()).isNotNull();
		assertThat(channel.threadMetadata().isArchived()).isTrue();
	}

	@Test
	void givenChannelJsonWithoutThreadMetadata_whenDeserialized_thenThreadMetadataNull() throws Exception {
		String json = "{\"id\":\"c1\",\"name\":\"Channel\",\"type\":0,\"parent_id\":\"cat-1\"}";

		Channel channel = MAPPER.readValue(json, Channel.class);

		assertThat(channel.threadMetadata()).isNull();
	}
}
