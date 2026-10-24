package com.ichichuang.cinema.data.catalog

import org.junit.Assert.*
import org.junit.Test

class WorkIdentityTest {
    private val verified = CatalogIdentifier("synthetic.namespace", "shared-17")
    @Test fun same_title_year_and_bare_numeric_id_are_only_a_candidate_without_verified_proof() {
        assertEquals(AssociationDecision.CANDIDATE, WorkIdentity.decide(null, emptySet(), emptySet(), true))
    }
    @Test fun untrusted_identifier_claim_is_not_an_association_proof() {
        assertEquals(AssociationDecision.UNRESOLVED, WorkIdentity.decide(null, setOf(verified), setOf(verified), false))
    }
    @Test fun verified_namespaced_identifier_can_link_compatible_groups() {
        assertEquals(AssociationDecision.LINKED, WorkIdentity.decide(verified, emptySet(), setOf(verified), false))
    }
    @Test fun conflicting_identifier_in_the_proof_namespace_fails_closed() {
        assertEquals(AssociationDecision.CONFLICT, WorkIdentity.decide(verified, setOf(CatalogIdentifier(verified.namespace, "different")), emptySet(), true))
    }
    @Test fun transitive_collision_in_another_namespace_also_fails_closed() {
        assertEquals(AssociationDecision.CONFLICT, WorkIdentity.decide(verified,
            setOf(CatalogIdentifier("other.namespace", "one")), setOf(CatalogIdentifier("other.namespace", "two")), false))
    }
    @Test fun fully_namespaced_identifiers_do_not_collide_by_bare_value() {
        assertEquals(AssociationDecision.LINKED, WorkIdentity.decide(verified,
            setOf(CatalogIdentifier("namespace.A", "17")), setOf(CatalogIdentifier("namespace.B", "17")), false))
    }
}
