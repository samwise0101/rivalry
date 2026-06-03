package com.samwise0101.rivalry;

import com.google.gson.Gson;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Reads group membership from the public Wise Old Man API. Used only to populate
 * the rival roster; all stat tracking still comes from the official hiscores.
 */
@Slf4j
public class WomClient
{
	private static final HttpUrl API_BASE = HttpUrl.get("https://api.wiseoldman.net/v2");
	private static final String USER_AGENT = "Rivalry RuneLite Plugin - https://github.com/samwise0101/rivalry";

	private final OkHttpClient okHttpClient;
	private final Gson gson;

	@Inject
	WomClient(OkHttpClient okHttpClient, Gson gson)
	{
		this.okHttpClient = okHttpClient;
		this.gson = gson;
	}

	/**
	 * Asynchronously fetches the members of a WOM group, sorted by most recent
	 * activity and limited to {@code maxMembers}. Callbacks run on an OkHttp thread.
	 */
	void fetchGroupMembers(int groupId, int maxMembers, Consumer<List<String>> onSuccess, Consumer<Exception> onError)
	{
		HttpUrl url = API_BASE.newBuilder()
			.addPathSegment("groups")
			.addPathSegment(Integer.toString(groupId))
			.build();

		Request request = new Request.Builder()
			.url(url)
			.header("User-Agent", USER_AGENT)
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				onError.accept(e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (Response r = response)
				{
					if (!r.isSuccessful() || r.body() == null)
					{
						onError.accept(new IOException("WOM returned HTTP " + r.code()));
						return;
					}

					WomGroup group = gson.fromJson(r.body().charStream(), WomGroup.class);
					if (group == null || group.memberships == null)
					{
						onError.accept(new IOException("Unexpected WOM response"));
						return;
					}

					List<String> members = new ArrayList<>();
					group.memberships.stream()
						.filter(m -> m.player != null && m.player.name() != null)
						// Most recently active first (ISO-8601 strings sort chronologically).
						.sorted(Comparator.comparing(
							(WomMembership m) -> m.player.lastChangedAt == null ? "" : m.player.lastChangedAt)
							.reversed())
						.limit(Math.max(1, maxMembers))
						.forEach(m -> members.add(m.player.name()));

					onSuccess.accept(members);
				}
				catch (Exception e)
				{
					onError.accept(e);
				}
			}
		});
	}

	// --- Minimal Gson models (unknown fields are ignored) ---

	private static class WomGroup
	{
		List<WomMembership> memberships;
	}

	private static class WomMembership
	{
		WomPlayer player;
	}

	private static class WomPlayer
	{
		String username;
		String displayName;
		String lastChangedAt;

		String name()
		{
			return displayName != null ? displayName : username;
		}
	}
}
