/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * License: GNU Lesser General Public License (LGPL), version 2.1 or later.
 * See the lgpl.txt file in the root directory or <http://www.gnu.org/licenses/lgpl-2.1.html>.
 */
package org.hibernate.userguide.envers;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Map;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.Id;
import javax.persistence.ManyToOne;
import javax.persistence.NoResultException;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.envers.AuditReaderFactory;
import org.hibernate.envers.Audited;
import org.hibernate.envers.configuration.EnversSettings;
import org.hibernate.envers.query.AuditEntity;
import org.hibernate.envers.strategy.ValidityAuditStrategy;
import org.hibernate.jpa.test.BaseEntityManagerFunctionalTestCase;

import org.junit.Test;

import static org.hibernate.testing.transaction.TransactionUtil.doInJPA;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

/**
 * @author Vlad Mihalcea
 */
public class QueryAuditTest extends BaseEntityManagerFunctionalTestCase {

	@Override
	protected Class<?>[] getAnnotatedClasses() {
		return new Class<?>[] {
			Customer.class,
			Address.class
		};
	}

	@Override
	protected void addConfigOptions(Map options) {
		options.put(
			EnversSettings.AUDIT_STRATEGY,
			ValidityAuditStrategy.class.getName()
		);
	}

	@Test
	public void test() {
		doInJPA( this::entityManagerFactory, entityManager -> {
			Address address = new Address();
			address.setId( 1L );
			address.setCountry( "România" );
			address.setCity( "Cluj-Napoca" );
			address.setStreet( "Bulevardul Eroilor" );
			address.setStreetNumber( "1 A" );
			entityManager.persist( address );

			Customer customer = new Customer();
			customer.setId( 1L );
			customer.setFirstName( "John" );
			customer.setLastName( "Doe" );
			customer.setAddress( address );

			entityManager.persist( customer );
		} );

		doInJPA( this::entityManagerFactory, entityManager -> {
			Customer customer = entityManager.find( Customer.class, 1L );
			customer.setLastName( "Doe Jr." );
		} );

		doInJPA( this::entityManagerFactory, entityManager -> {
			Customer customer = entityManager.getReference( Customer.class, 1L );
			entityManager.remove( customer );
		} );

		List<Number> revisions = doInJPA( this::entityManagerFactory, entityManager -> {
			 return AuditReaderFactory.get( entityManager ).getRevisions(
				Customer.class,
				1L
			);
		} );

		doInJPA( this::entityManagerFactory, entityManager -> {
			//tag::entities-at-revision-example[]
			Customer customer = (Customer) AuditReaderFactory.get( entityManager )
				.createQuery()
				.forEntitiesAtRevision( Customer.class, revisions.get( 0 ) )
				.getSingleResult();

			assertEquals("Doe", customer.getLastName());
			//end::entities-at-revision-example[]
		} );

		doInJPA( this::entityManagerFactory, entityManager -> {
			//tag::entities-filtering-example[]
			List<Customer> customers = AuditReaderFactory.get( entityManager )
				.createQuery()
				.forRevisionsOfEntity( Customer.class, true, true )
				.add( AuditEntity.property( "firstName" ).eq( "John" ) )
				.getResultList();

			assertEquals(2, customers.size());
			assertEquals( "Doe", customers.get( 0 ).getLastName() );
			assertEquals( "Doe Jr.", customers.get( 1 ).getLastName() );
			//end::entities-filtering-example[]
		} );

		doInJPA( this::entityManagerFactory, entityManager -> {
			//tag::entities-filtering-by-entity-example[]
			Address address = entityManager.getReference( Address.class, 1L );

			List<Customer> customers = AuditReaderFactory.get( entityManager )
				.createQuery()
				.forRevisionsOfEntity( Customer.class, true, true )
				.add( AuditEntity.property( "address" ).eq( address ) )
				.getResultList();

			assertEquals(2, customers.size());
			//end::entities-filtering-by-entity-example[]
		} );

		doInJPA( this::entityManagerFactory, entityManager -> {
			//tag::entities-filtering-by-entity-identifier-example[]
			List<Customer> customers = AuditReaderFactory.get( entityManager )
				.createQuery()
				.forRevisionsOfEntity( Customer.class, true, true )
				.add( AuditEntity.relatedId( "address" ).eq( 1L ) )
				.getResultList();

			assertEquals(2, customers.size());
			//end::entities-filtering-by-entity-identifier-example[]
		} );

		doInJPA( this::entityManagerFactory, entityManager -> {
			//tag::entities-in-clause-filtering-by-entity-identifier-example[]
			List<Customer> customers = AuditReaderFactory.get( entityManager )
				.createQuery()
				.forRevisionsOfEntity( Customer.class, true, true )
				.add( AuditEntity.relatedId( "address" ).in( new Object[] { 1L, 2L } ) )
				.getResultList();

			assertEquals(2, customers.size());
			//end::entities-in-clause-filtering-by-entity-identifier-example[]
		} );

		doInJPA( this::entityManagerFactory, entityManager -> {
			//tag::entities-filtering-and-pagination[]
			List<Customer> customers = AuditReaderFactory.get( entityManager )
				.createQuery()
				.forRevisionsOfEntity( Customer.class, true, true )
				.addOrder( AuditEntity.property( "lastName" ).desc() )
				.add( AuditEntity.relatedId( "address" ).eq( 1L ) )
				.setFirstResult( 1 )
				.setMaxResults( 2 )
				.getResultList();

			assertEquals(1, customers.size());
			//end::entities-filtering-and-pagination[]
		} );
	}

	@Audited
	@Entity(name = "Customer")
	public static class Customer {

		@Id
		private Long id;

		private String firstName;

		private String lastName;

		@Temporal( TemporalType.TIMESTAMP )
		@Column(name = "created_on")
		@CreationTimestamp
		private Date createdOn;

		@ManyToOne(fetch = FetchType.LAZY)
		private Address address;

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getFirstName() {
			return firstName;
		}

		public void setFirstName(String firstName) {
			this.firstName = firstName;
		}

		public String getLastName() {
			return lastName;
		}

		public void setLastName(String lastName) {
			this.lastName = lastName;
		}

		public Date getCreatedOn() {
			return createdOn;
		}

		public void setCreatedOn(Date createdOn) {
			this.createdOn = createdOn;
		}

		public Address getAddress() {
			return address;
		}

		public void setAddress(Address address) {
			this.address = address;
		}
	}

	@Audited
	@Entity(name = "Address")
	public static class Address {

		@Id
		private Long id;

		private String country;

		private String city;

		private String street;

		private String streetNumber;

		public Long getId() {
			return id;
		}

		public void setId(Long id) {
			this.id = id;
		}

		public String getCountry() {
			return country;
		}

		public void setCountry(String country) {
			this.country = country;
		}

		public String getCity() {
			return city;
		}

		public void setCity(String city) {
			this.city = city;
		}

		public String getStreet() {
			return street;
		}

		public void setStreet(String street) {
			this.street = street;
		}

		public String getStreetNumber() {
			return streetNumber;
		}

		public void setStreetNumber(String streetNumber) {
			this.streetNumber = streetNumber;
		}
	}
}
