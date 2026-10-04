package com.chunkblazer;

import java.awt.event.MouseEvent;
import javax.inject.Inject;
import javax.inject.Singleton;
import net.runelite.client.input.MouseAdapter;

/**
 * Makes the X on the Selected Task box clickable. Overlays can't take clicks by
 * themselves, so this listens to the game canvas (like TaskCardInput does for the
 * task cards) and swallows a click on the X so it doesn't also walk the player.
 * The overlay works out whether the X is hovered while drawing, so no coordinate
 * maths happens here.
 */
@Singleton
public class SelectedTaskOverlayInput extends MouseAdapter
{
	private final SelectedTaskOverlay overlay;
	private final ChunkBlazerPlugin plugin;

	@Inject
	private SelectedTaskOverlayInput(SelectedTaskOverlay overlay, ChunkBlazerPlugin plugin)
	{
		this.overlay = overlay;
		this.plugin = plugin;
	}

	@Override
	public MouseEvent mousePressed(MouseEvent event)
	{
		if (event.getButton() == MouseEvent.BUTTON1 && overlay.isCloseHovered())
		{
			plugin.clearSelectedTask();
			event.consume();
		}
		return event;
	}
}
