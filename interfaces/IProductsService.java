package com.wp.servicies.interfaces;

import com.wp.model.Product;
import com.wp.model.ProductGroupProperties;
import com.wp.model.objects.Nodes;
import com.wp.utils.mybatis.plugins.paging.page.PageContext;
import com.wp.web.forms.ProductForm;
import com.wp.web.views.*;

import java.util.List;
import java.util.Map;

/**
 * Данный класс представляет из себя интерфейс, для работы с товарами
 *
 * @author Ivan Yevsyukov
 * @e-mail ivan_yevsyukov@mail.ru
 */
public interface IProductsService {

    /**
     * Удаляет товар
     *
     * @param node_id индентификатор узла, на котором лежит товар
     * @param product_id индентификатор товара
     */
    void deleteProduct(Long node_id, Long product_id);

    /**
     * Обновляет конфигурации товаров
     *
     * @param group_id идентификатор группы товаров
     */
    void updateProductsGroupProperties(Long group_id);

    /**
     * Удаляет товар из папки товаров (при создании 1-ой конфигурации для товара создается "папка"
     * для этих товаров - сделано для облегчения работы)
     *
     * @param node_id индентификатор узла, на котором лежит товар
     * @param product_id индентификатор товара
     */
    void deleteProductFromFolder(Long node_id, Long product_id);

    /**
     * Создает "папку" для хранения конфигураций товаров
     *
     * @param product_id индентификатор товара
     * @return возвращает идентификатор созданной папки
     */
    Long createFolderForProduct(Long product_id);

    /**
     * Получает все данные о товаре (так называемую модель)
     *
     * @param product_id индентификатор товара
     * @return
     */
    Product getProductModel(Long product_id);

    /**
     * Получает данные о товаре, которые необходимы для просмотра пользователю
     *
     * @param product_id индентификатор товара
     * @return
     */
    ProductViewForView getProductView(Long product_id);

    /**
     * Получает данные о товаре, которые необходимы для формы редактирования
     *
     * @param product_id индентификатор товара
     * @return
     */
    ProductViewForEdit getProductForEdit(Long product_id);

    /**
     * Получает данные о товаре, которые необходимы для списка товаров
     *
     * @param product_id индентификатор товара
     * @return
     */
    ProductViewForList getProductForList(Long product_id);

    /**
     * Получает данные о товаре, которые необходимы для списка конфигураций одного товара
     *
     * @param product_id индентификатор товара
     * @return
     */
    ProductConfigurationsViewForList getProductConfigurationForListItem(Long product_id);

    /**
     * Сохраняет товар на узле
     *
     * @param node_id индентификатор узла, на котором лежит товар
     * @param product_id индентификатор товара
     * @param group_id идентификатор группы товаров
     * @param form форма с данными о товаре
     * @return
     */
    Long saveProduct(Long node_id, Long product_id, Long group_id, ProductForm form);

    /**
     * Получает список товаров для публичного просмотра
     *
     * @param section_id идентификатор раздела, в котором находятся товары
     * @param location_id идентификатор города, которому "принадлежат" товары
     * @param location_to_id идентификатор города, по которому разрешена доставка
     * @param pc контекст для постраничной навигации
     * @param filters заполненные фильтры
     * @return
     */
    List<ProductViewForList> getProductsForPublic(Long section_id, Long location_id, Long location_to_id,
                                                  PageContext pc, Map<String, String[]> filters);

    /**
     * Получает список товаров для конкретного узла
     *
     * @param node_id индентификатор узла, на котором лежит товар
     * @param is_active получить опубликованные/неопубликованные, удаленные товары (больше подходит Enum, заранее не
     * продумали)
     * @param section_id идентификатор раздела, в котором находятся товары
     * @param pc контекст для постраничной навигации
     * @param filters заполненные фильтры
     * @return
     */
    List<ProductViewForList> getProductsByNodeId(Long node_id, Long is_active, Long section_id, PageContext pc,
                                                 Map<String, String[]> filters);

    /**
     * Получает список товаров для конкретной конфигурации
     *
     * @param group_id идентификатор группы товаров
     * @param section_id идентификатор раздела, в котором находятся товары
     * @param pc контекст для постраничной навигации
     * @param filters заполненные фильтры
     * @return
     */
    List<ProductViewForList> getProductsByGroupId(Long group_id, Long section_id, PageContext pc,
                                                  Map<String, String[]> filters);

    /**
     * Получает список товаров для конкретной конфигурации в списке
     *
     * @param group_id идентификатор группы товаров
     * @param section_id идентификатор раздела, в котором находятся товары
     * @param pc контекст для постраничной навигации
     * @param filters заполненные фильтры
     * @return
     */
    List<ProductConfigurationsViewForList> getProductsByGroupIdForList(Long group_id, Long section_id, PageContext pc,
                                                                       Map<String, String[]> filters);

    /**
     * Получает список товаров для редактирования по конкретной группе
     *
     * @param group_id идентификатор группы товаров
     * @return
     */
    List<ProductViewForEdit> getProductsByGroupIdForEdit(Long group_id);

    /**
     * Получает все свойства конфигурации товаров
     *
     * @param group_id идентификатор группы товаров
     * @return
     */
    ProductGroupProperties getGroupPropertiesModel(Long group_id);

    /**
     * Данный метод проверяет уникальность конфигурации товара
     *
     * @param group_id идентификатор группы товаров
     * @param filters фильтры для текущего товара
     * @param node узел владельца товаров
     * @return уникален или неуникален товар в своей группе
     */
    boolean checkConfigurationForUnique(Long group_id, Long product_id, String filters, Nodes node);
}
