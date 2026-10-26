-- platform accounts that payouts move money through
insert into accounts (id, code, name, type, currency, balance, overdraft_allowed, version, created_at) values
    (gen_random_uuid(), 'platform:payouts-in-transit', 'Payouts in transit', 'LIABILITY', 'INR', 0, false, 0, now()),
    (gen_random_uuid(), 'platform:bank', 'Razorpay settlement balance', 'ASSET', 'INR', 0, true, 0, now()),
    (gen_random_uuid(), 'platform:payout-fees', 'Payout fee revenue', 'REVENUE', 'INR', 0, false, 0, now());

create table payouts (
    id                 uuid         primary key,
    wallet_account_id  uuid         not null references accounts (id),
    amount             bigint       not null check (amount > 0),
    fee                bigint       not null check (fee >= 0),
    currency           varchar(3)   not null,
    beneficiary        varchar(255) not null,
    status             varchar(16)  not null check (status in ('REQUESTED', 'PROCESSED', 'FAILED', 'REVERSED')),
    provider_payout_id varchar(64)  unique,
    failure_reason     varchar(512),
    version            bigint       not null default 0,
    created_at         timestamptz  not null,
    updated_at         timestamptz  not null
);

create index payouts_wallet_idx on payouts (wallet_account_id, created_at desc);
