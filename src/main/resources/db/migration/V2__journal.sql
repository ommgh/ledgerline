create table journal_entries (
    id          uuid         primary key,
    kind        varchar(32)  not null,
    currency    varchar(3)   not null,
    reference   varchar(128),
    description varchar(512),
    created_at  timestamptz  not null
);

create table postings (
    id               uuid        primary key,
    journal_entry_id uuid        not null references journal_entries (id),
    account_id       uuid        not null references accounts (id),
    direction        varchar(6)  not null check (direction in ('DEBIT', 'CREDIT')),
    amount           bigint      not null check (amount > 0),
    created_at       timestamptz not null
);

create index postings_account_created_idx on postings (account_id, created_at desc, id desc);
create index postings_entry_idx on postings (journal_entry_id);
