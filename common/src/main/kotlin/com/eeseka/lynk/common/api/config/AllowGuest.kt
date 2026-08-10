package com.eeseka.lynk.common.api.config

/**
 * Lets a guest reach a writing endpoint, which they are otherwise refused.
 *
 * Reads are open to guests already, so this is only ever needed on POST/PUT/PATCH/DELETE, and only for
 * the handful of things a guest legitimately does to their own throwaway account.
 */
annotation class AllowGuest