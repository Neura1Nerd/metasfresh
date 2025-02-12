/*
 * #%L
 * de.metas.contracts
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

package de.metas.contracts.modular.computing.salescontract.informative;

import de.metas.calendar.standard.YearAndCalendarId;
import de.metas.contracts.FlatrateTermId;
import de.metas.contracts.IFlatrateBL;
import de.metas.contracts.flatrate.TypeConditions;
import de.metas.contracts.model.I_C_Flatrate_Term;
import de.metas.contracts.modular.ComputingMethodType;
import de.metas.contracts.modular.ModularContractProvider;
import de.metas.contracts.modular.computing.AbstractComputingMethodHandler;
import de.metas.contracts.modular.log.LogEntryContractType;
import de.metas.contracts.modular.settings.ModularContractModuleId;
import de.metas.contracts.modular.settings.ModularContractSettings;
import de.metas.invoice.InvoiceLineId;
import de.metas.invoice.service.IInvoiceBL;
import de.metas.lang.SOTrx;
import de.metas.order.IOrderBL;
import de.metas.order.IOrderLineBL;
import de.metas.order.OrderId;
import de.metas.order.OrderLineId;
import de.metas.product.ProductId;
import de.metas.shippingnotification.ShippingNotificationLineId;
import de.metas.shippingnotification.ShippingNotificationRepository;
import de.metas.shippingnotification.model.I_M_Shipping_NotificationLine;
import de.metas.util.Services;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.adempiere.util.lang.impl.TableRecordReference;
import org.compiere.model.I_C_Invoice;
import org.compiere.model.I_C_InvoiceLine;
import org.compiere.model.I_C_Order;
import org.compiere.model.I_C_OrderLine;
import org.springframework.stereotype.Component;

import java.util.stream.Stream;

@Component
@RequiredArgsConstructor
public class SalesInformativeLogComputingMethod extends AbstractComputingMethodHandler
{
	@NonNull private final IOrderBL orderBL = Services.get(IOrderBL.class);
	@NonNull private final IOrderLineBL orderLineBL = Services.get(IOrderLineBL.class);
	@NonNull private final IFlatrateBL flatrateBL = Services.get(IFlatrateBL.class);
	@NonNull private final IInvoiceBL invoiceBL = Services.get(IInvoiceBL.class);
	@NonNull private final ModularContractProvider contractProvider;
	@NonNull private final ShippingNotificationRepository shippingNotificationRepository;
	@NonNull @Getter private final ComputingMethodType computingMethodType = ComputingMethodType.SalesInformativeLogs;

	@Override
	public boolean applies(final @NonNull TableRecordReference recordRef, final @NonNull LogEntryContractType contractType)
	{
		if (!contractType.isModularContractType())
		{
			return false;
		}

		switch (recordRef.getTableName())
		{
			case I_C_OrderLine.Table_Name ->
			{
				final I_C_Order orderRecord = orderBL.getById(orderLineBL.getOrderIdByOrderLineId(OrderLineId.ofRepoId(recordRef.getRecord_ID())));
				return SOTrx.ofBoolean(orderRecord.isSOTrx()).isSales();
			}
			case I_C_Flatrate_Term.Table_Name ->
			{
				final I_C_Flatrate_Term flatrateTermRecord = flatrateBL.getById(FlatrateTermId.ofRepoId(recordRef.getRecord_ID()));
				if (!TypeConditions.ofCode(flatrateTermRecord.getType_Conditions()).isModularContractType())
				{
					return false;
				}

				final OrderLineId orderLineId = OrderLineId.ofRepoIdOrNull(flatrateTermRecord.getC_OrderLine_Term_ID());
				if (orderLineId == null)
				{
					return false;
				}

				final OrderId orderId = orderLineBL.getOrderIdByOrderLineId(orderLineId);
				return SOTrx.ofBoolean(orderBL.getById(orderId).isSOTrx()).isSales();
			}
			case I_C_InvoiceLine.Table_Name ->
			{
				final I_C_Invoice invoiceRecord = invoiceBL.getByLineId(InvoiceLineId.ofRepoId(recordRef.getRecord_ID()));
				final SOTrx soTrx = SOTrx.ofBoolean(invoiceRecord.isSOTrx());

				return soTrx.isSales() && invoiceBL.isSalesFinalInvoiceOrFinalCreditMemo(invoiceRecord);
			}
			case I_M_Shipping_NotificationLine.Table_Name ->
			{
				final I_M_Shipping_NotificationLine line = shippingNotificationRepository.getLineRecordByLineId(ShippingNotificationLineId.ofRepoId(recordRef.getRecord_ID()));
				final I_C_Order salesOrder = orderBL.getById(OrderId.ofRepoId(line.getC_Order_ID()));
				final YearAndCalendarId yearAndCalendarId = YearAndCalendarId.ofRepoIdOrNull(salesOrder.getHarvesting_Year_ID(), salesOrder.getC_Harvesting_Calendar_ID());

				return yearAndCalendarId != null;
			}
			default -> {return false;}
		}
	}

	@Override
	public boolean isApplicableForSettings(final @NonNull TableRecordReference recordRef, final @NonNull ModularContractSettings settings)
	{
		return settings.getSoTrx().isSales();
	}

	@Override
	public @NonNull Stream<FlatrateTermId> streamContractIds(@NonNull final TableRecordReference recordRef)
	{
		return switch (recordRef.getTableName())
		{
			case I_C_OrderLine.Table_Name -> contractProvider.streamSalesContractsForSalesOrderLine(OrderLineId.ofRepoId(recordRef.getRecord_ID()));
			case I_C_Flatrate_Term.Table_Name -> Stream.of(FlatrateTermId.ofRepoId(recordRef.getRecord_ID()));
			case I_C_InvoiceLine.Table_Name -> contractProvider.streamModularSalesContractsForInvoiceLine(InvoiceLineId.ofRepoId(recordRef.getRecord_ID()));
			case I_M_Shipping_NotificationLine.Table_Name -> contractProvider.streamSalesContractsForShippingNotificationLine(ShippingNotificationLineId.ofRepoId(recordRef.getRecord_ID()));
			default -> Stream.empty();
		};
	}

	@Override
	public @NonNull Stream<ProductId> streamContractSpecificPricedProductIds(@NonNull final ModularContractModuleId moduleId)
	{
		return Stream.empty();
	}

}
