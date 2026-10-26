-- Splits an answer's input tokens the way Anthropic bills them.
--
-- Prompt caching is on, so most of what each model call sends is written to the cache once and
-- read back on the calls after it. Anthropic counts those apart from input_tokens and prices them
-- differently: a cache write costs more than plain input, a cache read a small part of it. With
-- only input_tokens the real cost of an answer could not be worked out. The three input columns
-- never overlap, so adding them gives everything the model read.
ALTER TABLE assistant_service.assistant_messages
    ADD COLUMN cache_creation_input_tokens integer,
    ADD COLUMN cache_read_input_tokens integer;
