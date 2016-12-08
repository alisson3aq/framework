/*
 * Demoiselle Framework
 *
 * License: GNU Lesser General Public License (LGPL), version 3 or later.
 * See the lgpl.txt file in the root directory or <https://www.gnu.org/licenses/lgpl.html>.
 */
package org.demoiselle.jee.core.api.persistence;

import java.util.List;

/**
 * 
 * @author SERPRO
 *
 */
public interface Result {

	public Integer getOffset();
	public void setOffset(Integer offset);
	
	public Integer getLimit();
	public void setLimit(Integer limit);
	
	public Long getCount();
	public void setCount(Long count);
	
	public List<?> getContent();
	public void setContent(List<?> content);
	
	Class<?> getEntityClass();
	void setEntityClass(Class<?> entityClass);

}
