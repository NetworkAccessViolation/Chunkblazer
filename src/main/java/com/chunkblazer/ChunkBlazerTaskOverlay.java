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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Point;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.ProgressBarComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;


public class ChunkBlazerTaskOverlay extends OverlayPanel
{
	private final Client client;
	private final ChunkBlazerPlugin plugin;
	private final ChunkBlazerConfig config;

	@Inject
	ChunkBlazerTaskOverlay(Client client, ChunkBlazerPlugin plugin, ChunkBlazerConfig config)
	{
		super(plugin);
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setLayer(OverlayLayer.UNDER_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (config.taskTrackerStyle() != TaskTrackerStyle.NET || client.getGameState() != GameState.LOGGED_IN)
		{
			return null;
		}
		NuzlockeTask selectedTask = plugin.getSelectedTask();
		if (selectedTask == null)
		{
			return null;
		}
		panelComponent.setGap(new Point(5, 1));

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Selected Task")
			.color(new Color(255, 152, 0))
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left(selectedTask.getName())
			.build());

		// Lazy hack to add more padding to the overlay
		panelComponent.getChildren().add(TitleComponent.builder()
			.text("")
			.build());

		String selDesc = selectedTask.getDescription();
		if (selDesc != null && !selDesc.trim().isEmpty())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left(selectedTask.getDescription())
				.leftColor(new Color(200, 200, 200))
				.build());
		}

		String info = NuzlockeTask.displayCategory(selectedTask.getCategory()) + " | " + selectedTask.getBasePoints() + " pts";
		if (selectedTask.getLevelRequirement() > 1)
		{
			info += " | L" + selectedTask.getLevelRequirement();
		}
		panelComponent.getChildren().add(LineComponent.builder()
			.left(info)
			.leftColor(new Color(255, 200, 100))
			.build());

		String selRegionName = plugin.getTaskRegionName(selectedTask);
		String regionRow = (selRegionName != null && !selRegionName.isEmpty())
			? "Chunk: " + selRegionName
			: "Chunk: unknown";
		panelComponent.getChildren().add(LineComponent.builder()
			.left(regionRow)
			.leftColor(new Color(140, 200, 230))
			.build());

		String selArea = plugin.getTaskArea(selectedTask);
		if (selArea != null && !selArea.isEmpty())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Area: " + selArea)
				.leftColor(new Color(180, 180, 220))
				.build());
		}


		final ProgressBarComponent progressBarComponent = new ProgressBarComponent();
		int progress = selectedTask.getCurrentProgress();
		int target = selectedTask.getTargetQuantity();

		progressBarComponent.setBackgroundColor(new Color(61, 56, 49));
		progressBarComponent.setForegroundColor(new Color(255, 152, 0));
		progressBarComponent.setValue(progress);
		progressBarComponent.setMaximum(target);
		progressBarComponent.setRightLabel(String.format("%d/%d", progress, target));
		progressBarComponent.setLabelDisplayMode(ProgressBarComponent.LabelDisplayMode.TEXT_ONLY);
		panelComponent.getChildren().add(progressBarComponent);
		return super.render(graphics);
	}

}
