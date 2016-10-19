/*
 * Demoiselle Framework
 *
 * License: GNU Lesser General Public License (LGPL), version 3 or later.
 * See the lgpl.txt file in the root directory or <https://www.gnu.org/licenses/lgpl.html>.
 */
package org.demoiselle.jee.core.api.security;

/**
 * Gerenciamento de tokens, onde nós armazenamos, criamos e apagamos os tokens
 * gerados pela app. Temos 3 sugestoes de implementação, a basica, JWT e
 * memória, mas você pode implementar a que mais for util para seu projeto ou
 * estender as existentes.
 *
 * Token Management, where we store, create and erase the tokens generated by
 * the app. We have three suggestions for implementation, basic, JWT and memory,
 * but you can implement more is useful for your project or extend existing
 * ones.
 *
 */
public interface TokensManager {

    /**
     * Busca o usuário logado que está sendo mantido local através do uso do
     * token que está em sessão de request Search the logged user being kept
     * place through token use that is in session request
     *
     * @return @see org.demoiselle.jee.core.api.security.DemoisellePrincipal
     */
    public DemoisellePrincipal getUser();

    /**
     * Armazena o usuário logado para ser utilizado nas próximas requisições,
     * nesse momento é gerado um token, dependendo da estratégia escolhida, e
     * colocado no objeto token no escopo de request
     *
     * Stores the user logged in to be used in the next requests at that time it
     * generates a token, depending on the approach, and placed in the token
     * object in the request scoped
     *
     * @param user @see org.demoiselle.jee.core.api.security.DemoisellePrincipal
     */
    public void setUser(DemoisellePrincipal user);

    /**
     * Valida se o usuário está armazenado de forma correta, dependendo da
     * estratégia escolhida. Validates that the user is stored correctly,
     * depending on the chosen strategy.
     *
     * @return
     */
    public boolean validate();

}
