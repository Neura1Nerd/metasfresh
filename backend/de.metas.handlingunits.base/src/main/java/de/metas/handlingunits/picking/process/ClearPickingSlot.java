/*
 * #%L
 * de.metas.handlingunits.base
 * %%
 * Copyright (C) 2024 metas GmbH
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 2 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public
 * License along with this program. If not, see
 * <http://www.gnu.org/licenses/gpl-2.0.html>.
 * #L%
 */

package de.metas.handlingunits.picking.process;

import de.metas.handlingunits.picking.PickingSlotService;
import de.metas.handlingunits.picking.requests.ReleasePickingSlotRequest;
import de.metas.picking.api.PickingSlotId;
import de.metas.process.IProcessPrecondition;
import de.metas.process.IProcessPreconditionsContext;
import de.metas.process.JavaProcess;
import de.metas.process.Param;
import de.metas.process.ProcessPreconditionsResolution;
import lombok.NonNull;
import org.compiere.SpringContextHolder;

public class ClearPickingSlot extends JavaProcess implements IProcessPrecondition
{
	private final SpringContextHolder.Lazy<PickingSlotService> pickingSlotServiceLazy = SpringContextHolder.lazyBean(PickingSlotService.class);

	@Param(parameterName = "ForceRemoveForOngoingJobs")
	private boolean forceRemoveForOngoingJobs;
	@Param(parameterName = "RemoveUnprocessedHUsFromSlot")
	private boolean removeUnprocessedHUsFromSlot;
	@Param(parameterName = "RemoveQueuedHUsFromSlot")
	private boolean removeQueuedHUsFromSlot;

	@Override
	public ProcessPreconditionsResolution checkPreconditionsApplicable(final @NonNull IProcessPreconditionsContext context)
	{
		if (!context.isSingleSelection())
		{
			return ProcessPreconditionsResolution.rejectBecauseNotSingleSelection();
		}

		return ProcessPreconditionsResolution.accept();
	}

	@Override
	protected String doIt() throws Exception
	{
		pickingSlotServiceLazy.get().releasePickingSlot(ReleasePickingSlotRequest.builder()
																.pickingSlotId(PickingSlotId.ofRepoId(getRecord_ID()))
																.isForceRemoveForOngoingJobs(forceRemoveForOngoingJobs)
																.removeUnprocessedHUsFromSlot(removeUnprocessedHUsFromSlot)
																.removeQueuedHUsFromSlot(removeQueuedHUsFromSlot)
																.build());

		return MSG_OK;
	}
}
