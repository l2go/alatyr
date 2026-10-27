// SPDX-License-Identifier: AGPL-3.0-or-later
// Copyright 2026 l2go

package world

import (
	"math"
	"slices"
	"strconv"
	"strings"

	"github.com/l2go/alatyr/engine/go/world/catalog"
	"github.com/l2go/alatyr/engine/go/world/itemlog"
)

const maxTradeRows = 128
const maxTradeQuantity = 1_000_000

// TradeOffer is the exact item, quantity bound and unit price of an affordance.
// List zero denotes a sale to the merchant; Object binds a carried inventory row.
type TradeOffer struct {
	List, Item, Object int32
	Quantity, Price    int64
}

// Trade returns typed terms only for a complete issued buy or sell affordance.
func (a Affordance) Trade() (TradeOffer, bool) {
	if a.Verb != VerbBuy && a.Verb != VerbSell || len(a.Parameters) != 5 || len(a.Cost) != 0 {
		return TradeOffer{}, false
	}
	var v TradeOffer
	for i, name := range []string{"list", "item", "object", "quantity", "price"} {
		p := a.Parameters[i]
		n, ok := p.Value.Integer()
		if p.Name != name || !ok {
			return TradeOffer{}, false
		}
		if i < 3 && (n < 0 || n > math.MaxInt32) {
			return TradeOffer{}, false
		}
		switch i {
		case 0:
			v.List = int32(n)
		case 1:
			v.Item = int32(n)
		case 2:
			v.Object = int32(n)
		case 3:
			v.Quantity = n
		case 4:
			v.Price = n
		}
	}
	ok := v.Item > 0 && v.Quantity > 0 && v.Quantity <= maxTradeQuantity && v.Price >= 0
	if a.Verb == VerbBuy {
		ok = ok && v.List > 0 && v.Object == 0 && v.Price > 0
	} else {
		ok = ok && v.List == 0 && v.Object > 0
	}
	return v, ok
}

// TradeList is an owned publication of merchant authority, not a trade outcome.
// Empty means the current sale list has no eligible rows.
type TradeList struct {
	stamp
	Dialogue Dialogue
	List     int32
	Sell     bool
}

func (TradeList) event() {}

func (w *World) merchantChoice(n *npc, action string) (int32, bool) {
	prefix := "npc_" + strconv.Itoa(int(n.id)) + "_"
	if action == prefix+"Sell" {
		return 0, w.cat.Currency() != 0
	}
	text, ok := strings.CutPrefix(action, prefix+"Buy ")
	if !ok {
		return 0, false
	}
	id, err := strconv.ParseInt(text, 10, 32)
	shop, found := w.cat.Shop(int32(id))
	return int32(id), err == nil && found && shop.NPC == n.tmpl.ID && strconv.FormatInt(id, 10) == text
}

func (w *World) showTrade(s *state, p *player, n *npc, list int32) bool {
	var offers []TradeOffer
	verb := VerbBuy
	if list != 0 {
		shop, ok := w.cat.Shop(list)
		if !ok || shop.NPC != n.tmpl.ID {
			return false
		}
		for _, row := range shop.Offers {
			offers = append(offers, TradeOffer{List: list, Item: row.Item, Quantity: maxTradeQuantity, Price: row.Price})
		}
	} else {
		verb = VerbSell
		for _, it := range p.char.Items {
			tmpl, ok := w.cat.Item(it.TemplateID)
			if ok && tmpl.Sellable && tmpl.ConsumeType != "asset" && !it.Equipped() && it.Count > 0 {
				offers = append(offers, TradeOffer{Item: it.TemplateID, Object: it.ObjectID, Quantity: min(it.Count, maxTradeQuantity), Price: tmpl.Price / 2})
			}
		}
	}
	if len(offers) > maxTradeRows || uint64(len(offers)) > ^uint64(0)-s.affordanceSeq || s.now() > Time(math.MaxInt64)-affordanceLifetime {
		return false
	}
	s.clearAffordances(p)
	for _, terms := range offers {
		a := s.nextAffordance(verb, n.ref())
		if a.ID == 0 {
			return false
		}
		a.Parameters = []Parameter{{"list", IntValue(int64(terms.List))}, {"item", IntValue(int64(terms.Item))}, {"object", IntValue(int64(terms.Object))}, {"quantity", IntValue(terms.Quantity)}, {"price", IntValue(terms.Price)}}
		p.affordances = append(p.affordances, a)
		s.change(p.ref(), PropertyAffordance, UintValue(0), UintValue(a.ID))
	}
	p.send(TradeList{Dialogue: w.dialogueOf(p), List: list, Sell: list == 0})
	return true
}

func (w *World) trade(s *state, p *player, req Request) {
	refuse := func(reason Reason) { s.resolve(StageRefused, reason) }
	a, ok := p.affordance(req.AffordanceID)
	if !ok || a.Verb != req.Verb || a.Target != req.Target || a.Expires <= s.now() || a.Issued > s.now() || s.ref(a.Target.ID) != a.Target {
		refuse(ReasonStale)
		return
	}
	terms, ok := a.Trade()
	if !ok || req.Quantity < 1 || req.Quantity > terms.Quantity {
		refuse(ReasonInvalid)
		return
	}
	n := s.npcs[a.Target.ID]
	if n == nil || n.tmpl.Role != catalog.Merchant || p.dead || p.teleporting || !w.canInteract(p, n) {
		refuse(ReasonState)
		return
	}
	tmpl, ok := w.cat.Item(terms.Item)
	if !ok || terms.Price > math.MaxInt64/req.Quantity || req.Verb == VerbBuy && !tmpl.Stackable() && req.Quantity != 1 {
		refuse(ReasonInvalid)
		return
	}
	total := terms.Price * req.Quantity
	currency := w.cat.Currency()
	coin := -1
	item := -1
	for i, it := range p.char.Items {
		if it.TemplateID == currency {
			if coin != -1 || it.Equipped() {
				refuse(ReasonState)
				return
			}
			coin = i
		}
		if req.Verb == VerbSell && it.ObjectID == terms.Object || req.Verb == VerbBuy && tmpl.Stackable() && it.TemplateID == terms.Item && !it.Equipped() {
			item = i
		}
	}
	coinBefore := int64(0)
	if coin >= 0 {
		coinBefore = p.char.Items[coin].Count
	}
	itemBefore := int64(0)
	if item >= 0 {
		itemBefore = p.char.Items[item].Count
	}
	delta := req.Quantity
	moneyDelta := -total
	if req.Verb == VerbBuy {
		shop, found := w.cat.Shop(terms.List)
		valid := found && shop.NPC == n.tmpl.ID
		found = false
		for _, row := range shop.Offers {
			if row.Item == terms.Item && row.Price == terms.Price {
				found = true
			}
		}
		if !valid || !found {
			refuse(ReasonStale)
			return
		}
		if coinBefore < total || itemBefore > math.MaxInt64-req.Quantity {
			refuse(ReasonState)
			return
		}
	} else {
		if item < 0 || !tmpl.Sellable || tmpl.ConsumeType == "asset" || p.char.Items[item].Equipped() || p.char.Items[item].TemplateID != terms.Item || itemBefore < req.Quantity || tmpl.Price/2 != terms.Price || coinBefore > math.MaxInt64-total {
			refuse(ReasonState)
			return
		}
		delta = -delta
		moneyDelta = total
	}
	// Count the committed rows, including rows removed by either side of the
	// exchange. Temporary zero rows must not consume the inventory allowance.
	rows := int64(len(p.char.Items))
	if item >= 0 {
		if itemBefore+delta == 0 {
			rows--
		}
	} else if req.Verb == VerbBuy && !tmpl.Stackable() {
		rows += req.Quantity
	} else {
		rows++
	}
	if coin >= 0 && coinBefore+moneyDelta == 0 {
		rows--
	} else if coin < 0 && moneyDelta != 0 {
		rows++
	}
	if rows > maxTradeRows {
		refuse(ReasonState)
		return
	}
	// Build the entire next inventory and reserve all new identities before
	// publishing either balance. A refusal cannot debit only one side.
	items := slices.Clone(p.char.Items)
	refs := make(map[int32]Ref, len(p.itemRefs)+2)
	for id, ref := range p.itemRefs {
		refs[id] = ref
	}
	changes := []ItemChange{}
	effects := []Effect{}
	assign := func(template int32, index int, amount int64) bool {
		before := int64(0)
		var it Item
		kind := "modified"
		if index >= 0 {
			it = items[index]
			before = it.Count
			it.Count += amount
		} else {
			id := s.newID()
			if id == 0 {
				return false
			}
			gen := s.incarnation()
			if gen == 0 {
				return false
			}
			it = Item{ObjectID: id, TemplateID: template, Count: amount, Slot: -1}
			refs[id] = Ref{ID: id, Gen: gen}
			kind = "added"
		}
		if it.Count == 0 {
			kind = "removed"
		}
		if index >= 0 {
			items[index] = it
		} else {
			items = append(items, it)
		}
		changes = append(changes, ItemChange{Kind: kind, Item: it})
		from := ResourceLocation{Place: PlaceMerchant}
		to := ResourceLocation{Entity: p.ref(), Item: refs[it.ObjectID], Place: PlaceInventory}
		balance := Balance{Before: IntegerAmount(before), After: IntegerAmount(it.Count)}
		e := ResourceTransferred{Resource: Resource{Kind: ResourceItem, TemplateID: template}, From: from, To: to, Amount: IntegerAmount(amount), ToBalance: balance}
		if amount < 0 {
			e.From, e.To = e.To, e.From
			e.FromBalance, e.ToBalance = e.ToBalance, e.FromBalance
			e.Amount = IntegerAmount(-amount)
		}
		effects = append(effects, e)
		return true
	}
	if req.Verb == VerbBuy && !tmpl.Stackable() {
		for range req.Quantity {
			if !assign(terms.Item, -1, 1) {
				refuse(ReasonState)
				return
			}
		}
	} else if !assign(terms.Item, item, delta) {
		refuse(ReasonState)
		return
	}
	if moneyDelta != 0 && !assign(currency, coin, moneyDelta) {
		refuse(ReasonState)
		return
	}
	items = slices.DeleteFunc(items, func(it Item) bool { return it.Count == 0 })
	// Payment may remove currency weight. Check the completed inventory rather
	// than current load plus the goods; loadItems saturates on overflow.
	if req.Verb == VerbBuy && w.loadItems(items) > int64(p.stats.MaxLoad()) {
		refuse(ReasonState)
		return
	}
	for _, change := range changes {
		if change.Item.Count == 0 {
			delete(refs, change.Item.ObjectID)
		}
	}
	p.char.Items, p.itemRefs = items, refs
	s.group(effects)
	// An issued merchant list remains valid until replacement or expiry.
	// Request replay belongs to the command/session layer, not the offer.
	s.finish(StageDone)
	p.send(InventoryChanged{Changes: changes, Load: w.load(p)})
	for _, change := range changes {
		op := itemlog.Change
		if change.Kind == "added" {
			op = itemlog.Create
		}
		if change.Kind == "removed" {
			op = itemlog.Delete
		}
		w.logItem(op, "Merchant", change.Item.ObjectID, change.Item.TemplateID, change.Item.Count, p, n.tmpl.Name)
	}
}
