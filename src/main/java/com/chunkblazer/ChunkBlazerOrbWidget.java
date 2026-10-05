/*
 * Copyright (c) 2026, NetworkAccessViolation
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.chunkblazer;

import java.awt.image.BufferedImage;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.FontID;
import net.runelite.api.events.PostClientTick;
import net.runelite.api.events.ScriptPostFired;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.SpriteID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetTextAlignment;
import net.runelite.api.widgets.WidgetType;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.util.ImageUtil;

public class ChunkBlazerOrbWidget
{
	private final Client client;
	private final ClientThread clientThread;
	private final EventBus eventBus;
	private final ChunkBlazerPlugin plugin;
	private final ChunkBlazerConfig config;

	private final int bossTokenOrbSpriteID = -900;

	private Widget bossTokenOrb = null;
	private Widget bossTokenText = null;
	private Widget pointsOrb = null;
	private Widget pointsText = null;
	private Widget wikiWidget;

	@Inject
	private ChunkBlazerOrbWidget(Client client, ClientThread clientThread, EventBus eventBus, ChunkBlazerPlugin plugin, ChunkBlazerConfig config)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.eventBus = eventBus;
		this.plugin = plugin;
		this.config = config;
	}

	@Subscribe
	public void onScriptPostFired(ScriptPostFired event)
	{
		//8222 is the ScriptID for orbs_redraw
		//Not defined in the ScriptID class so specify it manually
		if (event.getScriptId() == 8222)
		{
			clientThread.invoke(this::addOrbWidgets);
		}
	}

	@Subscribe
	public void onPostClientTick(PostClientTick event)
	{
		updateOrbs();
	}

	@Subscribe
	private void onConfigChanged(ConfigChanged ev)
	{
		if ("chunkblazer".equals(ev.getGroup()) && "showMinimapOrbs".equals(ev.getKey()))
		{
			clientThread.invoke(this::addOrbWidgets);
		}
	}


	private void addOrbWidgets()
	{
		cleanup();
		if (!config.showMinimapOrbs())
		{
			return;
		}
		Widget parentOrbsWidget = client.getWidget(InterfaceID.Orbs.UNIVERSE);
		if (parentOrbsWidget != null)
		{
			int bossOrbX = 80;
			int bossOrbY = 148;
			int pointsOrbX = 139;
			int pointsOrbY = 148;
			boolean moveWiki = true;
			boolean flip = false;
			if (!client.isResized())
			{
				bossOrbX = 190;
				bossOrbY = 71;
				pointsOrbX = 190;
				pointsOrbY = 37;
				moveWiki = false;
				flip = true;
				//Toplevel.ORBS doesn't extend all the way to the right of the canvas by default
				//Extend its width to prevent custom orbs from being truncated
				parentOrbsWidget.getParent().setOriginalWidth(246);
				parentOrbsWidget.getParent().revalidate();
			}
			bossTokenOrb = parentOrbsWidget.createChild(-1, WidgetType.LAYER);
			bossTokenText = createOrbWidget(bossTokenOrb, bossTokenOrbSpriteID, null, bossOrbX, bossOrbY, flip);
			pointsOrb = parentOrbsWidget.createChild(-1, WidgetType.LAYER);
			pointsText = createOrbWidget(pointsOrb, -1, "Pts", pointsOrbX, pointsOrbY, flip);
			wikiWidget = client.getWidget(InterfaceID.Orbs.WIKI);
			if (wikiWidget != null)
			{
				if (moveWiki)
				{
					wikiWidget.setForcedPosition(37, 155);
				}
				else
				{
					wikiWidget.setForcedPosition(-1, -1);
					wikiWidget.revalidate();
				}
			}
		}
	}

	/** One orb: frame, value text, and either an icon sprite or (when {@code label} is set) a short text label. */
	private Widget createOrbWidget(Widget widget, int iconSpriteID, String label, int x, int y, boolean flip)
	{
		Widget backing = widget.createChild(-1, WidgetType.GRAPHIC);
		backing.setFlippedHorizontally(flip);
		backing.setSpriteId(SpriteID.MINIMAP_ORB_FRAME);
		backing.setOriginalWidth(57);
		backing.setOriginalHeight(34);
		backing.revalidate();

		Widget text = widget.createChild(-1, WidgetType.TEXT);
		text.setText("0");
		text.setTextShadowed(true);
		text.setTextColor(0x00FF00);
		text.setFontId(FontID.PLAIN_11);
		text.setXTextAlignment(WidgetTextAlignment.CENTER);
		text.setYTextAlignment(WidgetTextAlignment.CENTER);
		if (flip)
		{
			text.setOriginalX(29);
		}
		else
		{
			text.setOriginalX(4);
		}

		text.setOriginalY(16);
		text.setOriginalWidth(23);
		text.setOriginalHeight(13);
		text.revalidate();

		Widget icon;
		if (label != null)
		{
			icon = widget.createChild(-1, WidgetType.TEXT);
			icon.setText(label);
			icon.setTextShadowed(true);
			icon.setTextColor(0xFF981F);
			icon.setFontId(FontID.BOLD_12);
			icon.setXTextAlignment(WidgetTextAlignment.CENTER);
			icon.setYTextAlignment(WidgetTextAlignment.CENTER);
		}
		else
		{
			icon = widget.createChild(-1, WidgetType.GRAPHIC);
			icon.setSpriteId(iconSpriteID);
		}
		icon.setOriginalWidth(26);
		icon.setOriginalHeight(26);
		if (flip)
		{
			icon.setOriginalX(4);
		}
		else
		{
			icon.setOriginalX(27);
		}
		icon.setOriginalY(4);
		icon.revalidate();

		widget.setOriginalWidth(57);
		widget.setOriginalHeight(34);
		widget.setOriginalX(x);
		widget.setOriginalY(y);
		widget.revalidate();

		return text;
	}

	private void removeWidget(Widget widget)
	{
		if (widget == null)
		{
			return;
		}

		Widget parent = widget.getParent();
		if (parent == null)
		{
			return;
		}

		Widget[] children = parent.getChildren();
		if (children == null || children.length <= widget.getIndex() || children[widget.getIndex()] != widget)
		{
			return;
		}
		children[widget.getIndex()] = null;
		widget.deleteAllChildren();
	}

	private void updateOrbs()
	{
		if (bossTokenText == null || pointsText == null)
		{
			return;
		}
		setTextIfChanged(bossTokenText, String.valueOf(plugin.getBossTokens()));
		setTextIfChanged(pointsText, compact(plugin.getTotalPoints()));
	}

	/** The orb fits about four digits, so 10,000 and up shows as "10k", like the game does. */
	static String compact(int value)
	{
		return value >= 10_000 ? (value / 1000) + "k" : String.valueOf(value);
	}

	private static void setTextIfChanged(Widget widget, String text)
	{
		if (!text.equals(widget.getText()))
		{
			widget.setText(text);
			widget.revalidate();
		}
	}

	private void cleanup()
	{
		if (wikiWidget != null)
		{
			wikiWidget.setForcedPosition(-1, -1);
			wikiWidget.revalidate();
		}
		if (bossTokenOrb != null)
		{
			if (!client.isResized() && bossTokenOrb.getParent() != null && bossTokenOrb.getParent().getParent() != null)
			{
				//Clear changes to the Toplevel.ORBS widget
				bossTokenOrb.getParent().getParent().setOriginalWidth(236);
				bossTokenOrb.getParent().getParent().revalidate();
			}
		}
		removeWidget(bossTokenOrb);
		removeWidget(pointsOrb);
	}

	public void shutDown()
	{
		eventBus.unregister(this);
		cleanup();
		bossTokenOrb = bossTokenText = pointsOrb = pointsText = wikiWidget = null;
		client.getSpriteOverrides().remove(bossTokenOrbSpriteID);
	}

	// Registered here, not in the constructor: plugins are injected at client load even when disabled
	public void startUp()
	{
		BufferedImage bossTokenOrbIcon = ImageUtil.loadImageResource(ChunkBlazerPlugin.class, "boss_token_icon.png");
		client.getSpriteOverrides().put(bossTokenOrbSpriteID, ImageUtil.getImageSpritePixels(bossTokenOrbIcon, client));
		eventBus.register(this);
		addOrbWidgets();
	}
}
