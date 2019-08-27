/*
 * Copyright 2019 Björn Kautler
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.kautler.test.spock;

import org.codehaus.groovy.ast.ASTNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.ModuleNode;
import org.codehaus.groovy.ast.expr.Expression;
import org.codehaus.groovy.control.SourceUnit;
import org.codehaus.groovy.transform.ASTTransformation;
import org.codehaus.groovy.transform.GroovyASTTransformation;
import org.spockframework.runtime.model.FeatureMetadata;

/**
 * An AST transformation that fixes the enclosing method setting
 * of anonymous inner classes which are not done correctly by Spock
 * transformation.
 *
 * <p>This is a work-around for https://github.com/spockframework/spock/pull/1027
 */
@GroovyASTTransformation
public class AnonymousClassTransform implements ASTTransformation {
    /**
     * The feature metadata annotation to recognize Spock feature methods.
     */
    private static final ClassNode featureMetadata = new ClassNode(FeatureMetadata.class);

    @Override
    public void visit(ASTNode[] nodes, SourceUnit source) {
        ((ModuleNode) nodes[0])
                .getClasses()
                .stream()
                .filter(clazz -> clazz.getEnclosingMethod() != null)
                .forEach(clazz -> {
                    MethodNode enclosingMethod = clazz.getEnclosingMethod();
                    String enclosingMethodName = enclosingMethod.getName();
                    enclosingMethod
                            .getDeclaringClass()
                            .getMethods()
                            .stream()
                            .filter(method -> method
                                    .getAnnotations()
                                    .stream()
                                    .anyMatch(annotation -> {
                                        Expression nameAttribute = annotation.getMember("name");
                                        return (nameAttribute != null)
                                                && featureMetadata.equals(annotation.getClassNode())
                                                && enclosingMethodName.equals(nameAttribute.getText());
                                    }))
                            .findAny()
                            .ifPresent(clazz::setEnclosingMethod);
                });
    }
}
