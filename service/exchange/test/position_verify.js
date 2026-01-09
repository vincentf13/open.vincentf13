// Common Position Verification Logic
// Requires global variables: expSide, expQty, expEntry, contractSize, mmr

var pos = response.body.data.find(p => p.instrumentId == 10001);
client.assert(pos != null, "Position not found");

var qty = parseFloat(pos.quantity);
var entry = parseFloat(pos.entryPrice);
var mark = parseFloat(pos.markPrice);
var margin = parseFloat(pos.margin);
var upnl = parseFloat(pos.unrealizedPnl);
var cumFee = parseFloat(pos.cumFee);

var expSide = client.global.get("expSide");
var expQty = parseFloat(client.global.get("expQty"));
var expEntry = parseFloat(client.global.get("expEntry"));
var size = parseFloat(client.global.get("contractSize"));
var mmr = parseFloat(client.global.get("mmr"));

// 1. 絕對值驗證
client.assert(pos.side === expSide, "Side mismatch. Exp: " + expSide + ", Got: " + pos.side);
client.assert(Math.abs(qty - expQty) < 0.0001, "Qty mismatch. Exp: " + expQty + ", Got: " + qty);
client.assert(Math.abs(entry - expEntry) < 0.0001, "Entry mismatch. Exp: " + expEntry + ", Got: " + entry);

// 2. 邏輯一致性驗證
// Unrealized PnL
var priceDiff = (pos.side === "LONG") ? (mark - entry) : (entry - mark);
var expectedUpnl = priceDiff * qty * size;
client.assert(Math.abs(upnl - expectedUpnl) < 0.01, "Upnl mismatch. Exp: " + expectedUpnl + ", Got: " + upnl);

// Margin Ratio: (Margin + Upnl) / (Mark * Qty * Size)
var notional = mark * qty * size;
var equity = margin + upnl;
var expectedRatio = notional === 0 ? 0 : equity / notional;
if (notional > 0) {
    client.assert(Math.abs(parseFloat(pos.marginRatio) - expectedRatio) < 0.001, "Margin Ratio mismatch");
}

// Liquidation Price
var marginPerUnit = margin / (qty * size);
var expectedLiq = 0;
if (pos.side === "LONG") {
    expectedLiq = (entry - marginPerUnit) / (1 - mmr);
} else {
    expectedLiq = (entry + marginPerUnit) / (1 + mmr);
}
// client.log("Liq Check: API=" + pos.liquidationPrice + ", Calc=" + expectedLiq);

client.assert(cumFee >= 0, "Cum Fee should be non-negative");
