package com.zoocraft.zoo;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.zoocraft.core.ledger.Ledger;
import com.zoocraft.core.ledger.Transaction;
import com.zoocraft.core.ledger.TransactionType;

/** Codecs for the pure-Java core types, which cannot reference Minecraft themselves. */
public final class ZooCodecs {
    public static final Codec<TransactionType> TRANSACTION_TYPE = Codec.STRING.xmap(TransactionType::valueOf, TransactionType::name);

    public static final Codec<Transaction> TRANSACTION = RecordCodecBuilder.create(i -> i.group(
            Codec.STRING.fieldOf("key").forGetter(Transaction::key),
            TRANSACTION_TYPE.fieldOf("type").forGetter(Transaction::type),
            Codec.LONG.fieldOf("amount").forGetter(Transaction::amount),
            Codec.LONG.fieldOf("gameTime").forGetter(Transaction::gameTime),
            Codec.STRING.fieldOf("actor").forGetter(Transaction::actor)
    ).apply(i, Transaction::new));

    public static final Codec<Ledger> LEDGER = RecordCodecBuilder.create(i -> i.group(
            Codec.LONG.fieldOf("balance").forGetter(Ledger::balance),
            Codec.LONG.fieldOf("totalAdmission").forGetter(Ledger::totalAdmission),
            Codec.LONG.optionalFieldOf("totalShopSales", 0L).forGetter(Ledger::totalShopSales),
            Codec.LONG.fieldOf("totalDeposits").forGetter(Ledger::totalDeposits),
            Codec.LONG.fieldOf("totalWithdrawals").forGetter(Ledger::totalWithdrawals),
            TRANSACTION.listOf().fieldOf("recent").forGetter(Ledger::recent),
            Codec.STRING.listOf().fieldOf("keys").forGetter(Ledger::keys)
    ).apply(i, Ledger::new));

    private ZooCodecs() {
    }
}
