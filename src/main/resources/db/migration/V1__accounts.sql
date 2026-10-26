create table accounts (
    id                uuid         primary key,
    code              varchar(64)  not null unique,
    name              varchar(255) not null,
    type              varchar(16)  not null check (type in ('ASSET', 'LIABILITY', 'EQUITY', 'REVENUE', 'EXPENSE')),
    currency          varchar(3)   not null,
    balance           bigint       not null default 0,
    overdraft_allowed boolean      not null default false,
    version           bigint       not null default 0,
    created_at        timestamptz  not null,
    -- the overdraft rule is also enforced in Account#apply; this is the last line of defence
    constraint balance_non_negative check (overdraft_allowed or balance >= 0)
);
