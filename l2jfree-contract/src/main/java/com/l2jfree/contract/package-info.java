/*
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 *
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */
/**
 * The contract between the login module and the world module.
 * <p>
 * Both modules depend on this package and on nothing of each other. The login module implements
 * {@link com.l2jfree.contract.LoginPort}, the world module implements {@link com.l2jfree.contract.WorldPort},
 * and the platform launcher hands each module the port of the other. The package holds types and interfaces
 * only and depends on nothing but the JDK.
 */
package com.l2jfree.contract;
