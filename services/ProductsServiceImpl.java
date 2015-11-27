package com.wp.servicies.impl;

import com.wp.annotation.OffTLU;
import com.wp.crypto.Hash;
import com.wp.model.Product;
import com.wp.model.ProductGroupProperties;
import com.wp.model.PropertyName;
import com.wp.model.composite.PropertySimple;
import com.wp.model.enums.StatesEnum;
import com.wp.model.enums.TypesEnum;
import com.wp.model.objects.Items;
import com.wp.model.objects.Nodes;
import com.wp.servicies.interfaces.*;
import com.wp.utils.Cast;
import com.wp.utils.Is;
import com.wp.utils.mybatis.plugins.Conditions.OPERANDS;
import com.wp.utils.mybatis.plugins.conditions.ExtendContext;
import com.wp.utils.mybatis.plugins.conditions.FilterContext;
import com.wp.utils.mybatis.plugins.paging.page.PageContext;
import com.wp.web.exceptions.InternalServerErrorException;
import com.wp.web.exceptions.NotFoundException;
import com.wp.web.forms.ProductForm;
import com.wp.web.views.*;
import com.wp.web.views.company.CompanyView;
import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.test.context.transaction.TransactionConfiguration;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.*;

/**
 * Данный сервис реализует методы интерфейса для работы с товарами
 *
 * @author Ivan Yevsyukov
 * @e-mail ivan_yevsyukov@mail.ru
 */
@Service
@TransactionConfiguration(transactionManager = "transactionPostgresManager")
@Transactional(rollbackFor = Exception.class)
public class ProductsServiceImpl extends AService<IProductsService> implements IProductsService {

    // <editor-fold defaultstate="collapsed" desc="Подключаемые сервисы">
    // Сервис для работы с разделами
    @Autowired
    ISectionsAndFiltersService _sectionsService;

    // Сервис для работы с изображениями
    @Autowired
    IImageService _imageService;

    // Сервис для работы с компаниями
    @Autowired
    ICompanyService _companyService;

    // Сервис для работы с товарами, связанный со сфинксом
    @Autowired
    ISphinxProductIndex _sphinxProductsIndex;
    // </editor-fold>

    // переменная, для работы с лог-файлами
    private Logger log = Logger.getLogger(ProductsServiceImpl.class);

    // <editor-fold defaultstate="collapsed" desc="Сохраняем товар">
    @Override
    @OffTLU
    public Long saveProduct(Long node_id, Long product_id, Long group_id, ProductForm form) {

        Items item;
        Long old_section_id = null;

        // Если добавляем новый товар
        if (product_id == 0) {
            // Создаем объект типа товар на узле с идентификатором node_id
            item = _io.createItem(new Nodes(node_id), TypesEnum.PRODUCTS, "product");
            if (Is.Empty(item)) {
                Logger.getLogger(ProductsServiceImpl22.class).error("Не удалость создать объект товара");
                return null;
            }
        }
        // Если обновляем старый
        else {
            // Получаем объект по идентификатору, выбирая также свойство PropertyName.SECTION (раздел, в котором
            // лежит товар)
            item = _io.getItemById(product_id, new ExtendContext(Items.class).names(PropertyName.SECTION));

            if (Is.Empty(item)) {
                throw new NotFoundException("Товар не найден");
            }

            Object section = item.getValue(PropertyName.SECTION);

            // Если раздел был сохранен как Items (объект)
            if (section instanceof Items) {
                old_section_id = ((Items) section).getId();
            }
            // Если как Long
            else {
                old_section_id = (Long) section;
            }
        }

        // Свои заморочки для поля с ценой товара, которое сделано фильтром (удобство поиска)
        // тут мы получили псевдоним для фильтра с ценой (обычно этот alias == "price")
        // В дальнейшем нам это будет необходимо, чтобы из всех фильтров узнать значение цены
        // для товара (ключом будет как раз этот alias)
        form.setPrice_alias(getAliasForPrice(form.getSection_id()));

        // Перед добавлением/сохранением товара очищаем все его свойства
        _sectionsService.clearFilterProperties(item);

        // Сохраняем все его новые свойства
        _cs.savePropertiesList(item, false, form.toProps());

        // Если раздел изменился, то удаляем товар со старого раздела в сфинксе
        if(old_section_id != null && form.getSection_id() != old_section_id) {
            _sphinxProductsIndex.delete(old_section_id, product_id);
        }

        // Формируем данные для запись в сфинкс
        SphinxIndexItem sphinx_item = new SphinxIndexItem();

        // раздел для товара
        sphinx_item.setSection_id(form.getSection_id());

        // идентификатор товара
        sphinx_item.setId(item.getId());

        // описание товара
        sphinx_item.setText(form.getDescription());

        // название товара
        sphinx_item.setTitle(form.getTitle());

        // фильтры для товара
        sphinx_item.setFilters(form.getFiltersMap());

        // идентификатор узла, на котором сохранен товар
        sphinx_item.setShop_id(node_id);

        // опубликованыый или неопубликованный товар
        sphinx_item.setActive(form.getIs_publish());

        CompanyView company = _companyService.getCompanyMainDataById(node_id);

        List<Long> loc = new ArrayList<Long>();
        loc.add(company.getLocation().getId());

        // список городов, в которых продается товар
        sphinx_item.setLocations(loc);

        if (!Is.Empty(form.getGroup_id()) && form.getGroup_id() > 0) {
            // идентификатор группы, если товар является конфигурацией другого товара
            sphinx_item.setGroup_id(form.getGroup_id());
        }

        // Расчитываем вес товара
        long weight = 0l;
        weight += !Is.Empty(form.getImages()) ? 2 : 0;
        if (!Is.Empty(form.getPrice_alias()) && form.getFiltersMap() != null &&
            form.getFiltersMap().containsKey(form.getPrice_alias())) {
            weight += 1;
        }

        // вес товара
        sphinx_item.setProduct_weight(weight);

        // добавляем товар в индекс сфинкса
        _sphinxProductsIndex.addToIndex(sphinx_item);

        if (group_id > 0) {
            // Обновляем конфигурации для товара (все конфигурации товара лежат в одной папке, сделано для быстроты
            // выборки данных по конфигурациям)
            This().updateProductsGroupProperties(group_id);
        }

        return item.getId();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Получаем модель товара">
    @Override
    @OffTLU
    // Если выполнялись операции по сохранения товара и при выборке товара по id возникла ошибка 404, то откатываем
    // все манипуляции с БД, которые вызывались до этого
    @Transactional(readOnly = true, noRollbackFor = NotFoundException.class)
    public Product getProductModel(Long product_id) {

        FilterContext fc = new FilterContext();

        // Формируем контекст для условие "WHERE" в PostgreSQL и говорим, что хотим получать
        // только опубликованные или одобренные товары
        fc.where()  .variable(PropertyName.STATE_ID).operand(OPERANDS.IN)
                    .value(new Long[]{StatesEnum.ACTIVE.getState().getId(), StatesEnum.APPROVED.getState().getId()});

        // Получаем объект товара по его id со всеми свойствами
        Items item = _io.getItemByIdAndType(product_id, TypesEnum.PRODUCTS,
                new ExtendContext(Items.class).names("*"), fc);

        if (Is.Empty(item)) {
            throw new NotFoundException("Товар не найден");
        }

        // Преобразуем данные в класс (модель), для дальнейшего удобства
        return new Product(item);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Создаем папку для похожих товаров">
    @Override
    @OffTLU
    public Long createFolderForProduct(Long product_id) {

        // Получаем модель товара со всем свойствами
        Product product = This().getProductModel(product_id);

        // Создаем папку на узле product.getNode_id(), в которой будет хранится информация о
        // всех конфигурациях товара
        Items productFolderItem = _io.createItem(   new Nodes(product.getNode_id()), TypesEnum.FOLDER,
                                                    PropertyName.PRODUCT_GROUP);

        if (Is.Empty(productFolderItem)) {
            throw new InternalServerErrorException();
        }

        // Обновляем свойства PropertyName.GROUP_ID у товара, который был одиночным и стал конфигурацией
        _cs.saveProperties( new Items(product_id), false,
                            new PropertySimple(PropertyName.GROUP_ID, productFolderItem.getId()));

        SphinxIndexItem sphinx_item = new SphinxIndexItem();

        sphinx_item.setSection_id(product.getSection_id());
        sphinx_item.setId(product_id);
        sphinx_item.setGroup_id(productFolderItem.getId());

        // обновляем свойство группы для товара в сфинксе
        _sphinxProductsIndex.update(sphinx_item);

        return productFolderItem.getId();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Обновляем общие данные для группы товаров">
    @Override
    @OffTLU
    public void updateProductsGroupProperties(Long group_id) {

        List<ProductViewForEdit> products = This().getProductsByGroupIdForEdit(group_id);

        if (products.size() == 0) {
            log.error(  "Возникла непредвиденная ошибка при удалении товара, так как товар не может состоять " +
                        "в группе товаров и быть единственным в ней");
            return;
        }

        // Если товар в конфигурации один
        if (products.size() == 1) {
            // удаляем папку
            Items item = _io.getItemById(group_id);
            _io.toState(item, StatesEnum.REMOVED);

            // переносим товар из папки
            This().deleteProductFromFolder(products.get(0).getNode_id(), products.get(0).getId());
            return;
        }

        // Неинтересные обработки для свойств и фильтров для товаров
        List<PropertySimple> properties = processingFiltersFromConfigurations(products);

        try {
            ProductGroupProperties group_properties = This().getGroupPropertiesModel(group_id);

            // обновляем конфигурации для товаров
            _cs.savePropertiesList(new Items(group_properties.getId()), false, properties);
        }
        catch (Exception ex) {
            log.error("Произошла ошибка при обработке свойств для группы товаров", ex);
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Получаем данные о товаре при просмотре">
    @Override
    @OffTLU
    public ProductViewForView getProductView(Long product_id) {

        // Получаем модель товара со всем свойствами
        Product product = This().getProductModel(product_id);

        // Берем только нужные свойства для конкретного случая
        ProductViewForView view = new ProductViewForView(product);

        List<ImageView> images = new ArrayList<ImageView>();

        for (Long image_id : product.getImages()) {
            images.add(_imageService.getImageInfo(image_id));
        }

        // Получаем список фотографий к товару
        view.setImages(images);

        // Устанавливаем все указанные характеристики товара, которые учавствуют в поиске (фильтры)
        view.setFilters(_sectionsService.getFiltersByMapForView(product.getSection_id(), product.getFilters(), false));

        // Если этот товар состоит в группе товаров, то получаем все его характеристики
        if (product.getGroup_id() != null && product.getGroup_id() > 0) {
            try {
                // устанавливаем все характеристики для товара
                view.setFilters_configurations(This().getGroupPropertiesModel(product.getGroup_id()).getFilters());
            }
            catch (Exception ex) {
                // логируем ошибки
                log.warn(   String.format("Произошла ошибка при обработке свойств для группы товаров %s.",
                            product.getGroup_id()), ex);
            }
        }

        return view;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Получаем данные о товаре при открытии его на редактирование">
    @Override
    @OffTLU
    public ProductViewForEdit getProductForEdit(Long product_id) {

        // Получаем модель товара со всем свойствами
        Product product = This().getProductModel(product_id);

        // Берем только нужные свойства для конкретного случая
        ProductViewForEdit view = new ProductViewForEdit(product);

        List<ImageView> images = new ArrayList<ImageView>();

        for (Long image_id : product.getImages()) {
            images.add(_imageService.getImageInfo(image_id));
        }

        // Получаем список фотографий к товару
        view.setImages(images);

        // Устанавливаем все указанные характеристики товара, которые учавствуют в поиске (фильтры)
        view.setFilters(_sectionsService.getFiltersByMapForEdit(product.getSection_id(), product.getFilters()));

        return view;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Получаем список товаров">
    @Override
    @OffTLU
    // откатываем все операции с БД в случае ошибки получения товара для списка
    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public ProductViewForList getProductForList(Long product_id) {

        // Получаем модель товара со всем свойствами
        Product product = This().getProductModel(product_id);

        // Берем только нужные свойства для конкретного случая
        ProductViewForList product_list = new ProductViewForList(product);

        if(!product.getImages().isEmpty()){
            // устанавливем главную фотографию для товара, которая будет видна в списке
            product_list.setImage(_imageService.getImageInfo(product.getImages().get(0)));
        }

        if (product_list.getGroup_id() == null || product_list.getGroup_id() == 0) {
            return product_list;
        }

        try {
            // Если этот товар является конфигурацией другого товара, то получаем все свойства для конфигураций
            // (цвет, размер и др. свойства)
            product_list.setProperties(new ProductPropertiesForList(This().getGroupPropertiesModel(product.getGroup_id())));
        }
        catch (Exception ex) {
            // логируем ошибки
            log.error("Произошла ошибка при обработке свойств для группы товаров", ex);
        }

        return product_list;

    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Проверяем уникальность конфигурации товара">
    @Override
    @OffTLU
    public boolean checkConfigurationForUnique(Long group_id, Long product_id, String filters, Nodes node) {

        List<ProductViewForEdit> products = This().getProductsByGroupIdForEdit(group_id);
        String current_product_hash = Hash.getHex(filters);

        // Проверяем схожесть конфигураций и если хоть с одним товаром данная конфигурация совпадает,
        // то она не уникальна
        for (ProductViewForEdit p : products){
            if (p.getHash().equals(current_product_hash) && product_id.longValue() != p.getId()){
                return false;
            }
        }

        return true;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Получаем все данные о конфигурации товара">
    @Override
    @OffTLU
    public ProductConfigurationsViewForList getProductConfigurationForListItem(Long product_id) {

        // Получаем модель товара со всем свойствами
        Product p = This().getProductModel(product_id);

        // Извлекаем из модели конфигурации для списка
        ProductConfigurationsViewForList view = new ProductConfigurationsViewForList(p);

        // Получаем фильтры для конкретного товара
        List<FilterView> p_filters = _sectionsService.getFiltersByMapForEdit(p.getSection_id(), p.getFilters());

        if(!p.getImages().isEmpty()){
            // устанавливем главную фотографию для товара, которая будет видна в списке
            view.setImage(_imageService.getImageInfo(p.getImages().get(0)));
        }

        return view;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Получаем все данные о товарах для паблика">
    @Override
    public List<ProductViewForList> getProductsForPublic(Long section_id, Long location_id, Long location_to_id,
                                                         PageContext pc, Map<String, String[]> filters) {
        return getProducts(0l, 0l, section_id, location_id, location_to_id, 1l, pc, filters);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Получаем все данные о товарах для владельца">
    @Override
    public List<ProductViewForList> getProductsByNodeId(Long node_id, Long is_active, Long section_id, PageContext pc,
                                                        Map<String, String[]> filters) {
        return getProducts(node_id, 0l, section_id, null, null, is_active, pc, filters);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Получаем все товары одной конфигурации">
    @Override
    public List<ProductViewForList> getProductsByGroupId(Long group_id, Long section_id, PageContext pc,
                                                         Map<String, String[]> filters) {
        return getProducts(0l, group_id, section_id, null, null, null, pc, filters);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Получаем все конфигурации для данного товара">
    @Override
    public List<ProductConfigurationsViewForList> getProductsByGroupIdForList(Long group_id,
                                                                              Long section_id,
                                                                              PageContext pc,
                                                                              Map<String, String[]> filters) {

        // Получаем идентификаторы товаров по переданным параметрам
        List<Long> products_ids = getProductsIds(0l, group_id, section_id, null, null, null, pc, filters);

        List<ProductConfigurationsViewForList> products = new ArrayList<ProductConfigurationsViewForList>();

        for (Long product_id : products_ids) {
            try {
                // Получаем товар для списка (все запросы кэшированы и выполняются мгновенно)
                products.add(This().getProductConfigurationForListItem(product_id));
            }
            catch (Exception ex) {
                log.error("Не удалось получить данные о товаре.", ex);
            }
        }

        return products;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Получаем все конфигурации для данного товара при редактировании">
    @Override
    @OffTLU
    public List<ProductViewForEdit> getProductsByGroupIdForEdit(Long group_id) {

        // Получаем идентификаторы товаров по переданным параметрам
        List<Long> products_ids = getProductsIds(0l, group_id, 0l, null, null, null, null, null);

        List<ProductViewForEdit> products = new ArrayList<ProductViewForEdit>();

        for (Long product_id : products_ids) {
            try {
                // Получаем товар для редактирования (все запросы кэшированы и выполняются мгновенно)
                products.add(This().getProductForEdit(product_id));
            }
            catch (Exception ex) {
                log.error("Не удалось получить данные о товаре.", ex);
            }
        }

        return products;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Получаем модель конфигурации товара">
    @Override
    // Если отсутствует папка, в которой хранятся товары и все их свойства для конфигураций, откатываем
    // все манипуляции с БД, которые были раньше
    @Transactional(readOnly = true, noRollbackFor = NotFoundException.class)
    public ProductGroupProperties getGroupPropertiesModel(Long group_id) {

        Items item = _io.getItemByIdAndType(group_id, TypesEnum.FOLDER,
                new ExtendContext(Items.class).names("*"));

        if (Is.Empty(item)) {
            throw new NotFoundException("Не найдена папка для товаров");
        }

        return new ProductGroupProperties(item);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Удаляем товар">
    @Override
    @OffTLU
    public void deleteProduct(Long node_id, Long product_id) {

        // Получаем модель товара со всем свойствами
        Product product = This().getProductModel(product_id);

        if (product.getNode_id() != node_id) {
            throw new NotFoundException();
        }

         Items item = _io.getItemById(product_id);

         // меняем объекту статус на удаленный
        _io.toState(item, StatesEnum.REMOVED);

        // удаляем товар из сфинкса
        _sphinxProductsIndex.delete(product.getSection_id(), product_id);

        // Если товар находился в группе товаров
        if (!Is.Empty(product.getGroup_id()) && product.getGroup_id() > 0) {
            // обновляем свойства группы товаров
            This().updateProductsGroupProperties(product.getGroup_id());
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Удаляем товар из группы товаров">
    @Override
    @OffTLU
    public void deleteProductFromFolder(Long node_id, Long product_id) {

        // Получаем модель товара со всем свойствами
        Product product = This().getProductModel(product_id);

        if (product.getNode_id() != node_id) {
            throw new NotFoundException();
        }

        // Удаляем свойство группы для товара, так как он становится одиночным и больше не учавствует в конфигурации
        _cs.saveProperties(new Items(product_id), PropertySimple.clear(PropertyName.GROUP_ID));

        SphinxIndexItem sphinx_item = new SphinxIndexItem();

        sphinx_item.setSection_id(product.getSection_id());
        sphinx_item.setId(product_id);
        sphinx_item.setGroup_id(0);

        // удаляем свойство группы для товара из сфинкса
        _sphinxProductsIndex.update(sphinx_item);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Вспомогательные методы">
    private List<ProductViewForList> getProducts(Long node_id,
                                                 Long group_id,
                                                 Long section_id,
                                                 Long location_id,
                                                 Long location_to_id,
                                                 Long is_active,
                                                 PageContext pc,
                                                 Map<String, String[]> filters) {

        // Получаем идентификаторы товаров по переданным параметрам
        List<Long> products_ids = getProductsIds(node_id, group_id, section_id, location_id, null, is_active, pc, filters);

        List<ProductViewForList> products = new ArrayList<ProductViewForList>();

        for (Long product_id : products_ids) {
            try {
                // Получаем товар для списка (все запросы кэшированы и выполняются мгновенно)
                products.add(This().getProductForList(product_id));
            } catch (Exception ex) {
                log.warn(String.format("Не удалось получить данные о товаре %s.", product_id), ex);
            }
        }

        return products;

    }

    private List<Long> getProductsIds(Long node_id, Long group_id, Long section_id, Long location_id,
                                      Long location_to_id, Long is_active, PageContext pc,
                                      Map<String, String[]> filters) {

        // формируем данные для выборки из сфинкса
        SphinxSelectSettings settings = new SphinxSelectSettings();

        if(is_active != null){
            List<Long> statuses = new ArrayList<Long>();
            int status = is_active == 1 ? StatesEnum.PUBLISHED.getValue() : StatesEnum.ACTIVE.getValue();
            statuses.add((long) status);

            // устанавливаем статусы для товаров
            settings.setStatus(statuses);
        }

        if (node_id != null && node_id > 0) {
            // идентификатор магазина, в котором находится товар
            settings.setShop_id(node_id);

            // группировка товаров по DESC и дате добавления товаров
            settings.setOrder_by(ISphinxProductIndex.DATE);
        }
        else if(is_active != null){
            // сортировка по весу товаров
            settings.setOrder_by(ISphinxProductIndex.PRODUCT_WEIGHT_DATE);
        }

        if(location_to_id != null){
            // список городов, в которых осуществляется доставка товаров
            settings.setLocationTo(location_to_id);
        }

        // идентификатор группы
        settings.setGroup_id(group_id > 0 ? group_id : ISphinxProductIndex.GROUP_ID);

        // раздел, в котором лежит товар
        settings.setSection(section_id != null && section_id > 0 ?
                new Items(section_id) :
                new Items(_sphinxProductsIndex.getRootId()));

        if (location_id != null && location_id != 0) {
            // город, где продается товар
            settings.setLocation(location_id);
        }

        if (pc != null) {
            // контекст доя постраничной нафигации
            settings.setPc(pc);
        }

        if (filters != null) {
            // фильтры для товаров
            settings.setFilters(_sectionsService.getFiltersValuesForSphinx(filters));
        }

        // получаем список товаров по переданным параметрам из сфинкса
        return _sphinxProductsIndex.select(settings);

    }

    private List<PropertySimple> processingFiltersFromConfigurations(List<ProductViewForEdit> products) {

        List<PropertySimple> properties = new ArrayList<PropertySimple>();

        Long min_price = null;
        List<Long> prices = new ArrayList<Long>();
        List<Long> colors_ids = new ArrayList<Long>();
        List<Long> sizes_ids = new ArrayList<Long>();
        List<ListValue> colors_values = new ArrayList<ListValue>();
        List<ListValue> sizes_values = new ArrayList<ListValue>();
        List<FilterConfigurationView> filters = new ArrayList<FilterConfigurationView>();
        HashMap<Long, Integer> filters_indexes = new HashMap<Long, Integer>();
        String dimension_system = null;

        for (ProductViewForEdit p : products) {
            try {
                if (!Is.Empty(p.getFilters()) && !p.getFilters().isEmpty()) {
                    for (FilterView f : p.getFilters()) {
                        FilterConfigurationView filter = new FilterConfigurationView();

                        if (filters_indexes.containsKey(f.getId())) {
                            filter = filters.get(filters_indexes.get(f.getId()));
                        }

                        if (f.getAlias().equals("price")) {
                            // Если у нас нет ещё такой цены в массиве
                            if (!prices.contains(Cast.toLong(f.getValue())) && !f.getValue().trim().equals("")) {
                                prices.add(Cast.toLong(f.getValue()));
                            }
                        }

                        if (f.getAlias().equals("color")) {
                            processingSizeAndColorsValuesIds(f, colors_ids, colors_values);
                        }

                        if (f.getAlias().equals("dimension")) {
                            processingSizeAndColorsValuesIds(f, sizes_ids, sizes_values);
                            dimension_system = p.getDimension();
                        }

                        // обработка остальных фильтров
                        if (f.getType().equals("number") || f.getType().equals("interval")) {
                            if (Is.Empty(f.getValue()) || Cast.toLong(f.getValue()) <= 0)
                                continue;

                            Long value = Cast.toLong(f.getValue());

                            if (Is.Empty(filter.getMin_value()) || value.longValue() < filter.getMin_value().longValue()) {
                                filter.setMin_value(value);
                            }

                            if (Is.Empty(filter.getMax_value()) || value.longValue() > filter.getMax_value().longValue()) {
                                filter.setMax_value(Cast.toLong(f.getValue()));
                            }
                        } else if (f.getType().equals("select")) {
                            if (Is.Empty(f.getSelectedValues()) || f.getSelectedValues().isEmpty())
                                continue;

                            if (Is.Empty(filter.getValues()) || filter.getValues().isEmpty()) {
                                filter.setValues(f.getSelectedValues());
                            } else {
                                for (Long v : f.getSelectedValues()) {
                                    if (!filter.getValues().contains(v)) {
                                        filter.getValues().add(v);
                                    }
                                }
                            }
                        } else if (f.getType().equals("radio")) {
                            if (Is.Empty(f.getSelectedValue()) || f.getSelectedValue() <= 0)
                                continue;

                            if (Is.Empty(filter.getValues()) || filter.getValues().isEmpty()) {
                                List<Long> values = new ArrayList<Long>();
                                values.add(f.getSelectedValue());
                                filter.setValues(values);
                            } else {
                                if (!filter.getValues().contains(f.getSelectedValue())) {
                                    filter.getValues().add(f.getSelectedValue());
                                }
                            }
                        }

                        if (filters_indexes.containsKey(f.getId()))
                            continue;

                        filter.setId(f.getId());
                        filter.setType(f.getType());

                        filters.add(filter);
                        filters_indexes.put(f.getId(), (filters.size() - 1));
                    }
                }
            } catch (Exception ex) {
                log.error("Не удалось обработать фильтры для товаров.", ex);
            }
        }

        if (prices.size() > 1) {
            Collections.sort(prices);
            properties.add(new PropertySimple(PropertyName.MIN_PRICE, prices.get(0)));
        }

        String colors_names = processingSizeAndColorsValuesNames(colors_ids, colors_values);
        String sizes_names = processingSizeAndColorsValuesNames(sizes_ids, sizes_values);

        if (colors_names != null) {
            properties.add(new PropertySimple(PropertyName.COLORS_NAMES, colors_names));
        }

        if (dimension_system != null) {
            properties.add(new PropertySimple(PropertyName.DIMENSION_SYSTEM, dimension_system));
        }

        if (sizes_names != null) {
            properties.add(new PropertySimple(PropertyName.SIZES, sizes_names));
        }

        for (int i = 0; i < filters.size(); i++) {
            FilterConfigurationView f = filters.get(i);
            if (f.getType().equals("number") || f.getType().equals("interval")) {
                if (f.getMin_value().longValue() == f.getMax_value().longValue()) {
                    filters.remove(i);
                    i--;
                }
            } else if (f.getType().equals("select") || f.getType().equals("radio")) {
                if (f.getValues().size() == 1) {
                    filters.remove(i);
                    i--;
                }
            }
        }

        if (!filters.isEmpty()) {
            String filters_json = "";
            try {
                filters_json = new ObjectMapper().writeValueAsString(filters);
            } catch (IOException ex) {
                log.error("Произошла ошибка при сериализации фильтров", ex);
            }

            if (!Is.Empty(filters_json) && !filters_json.equals("")) {
                properties.add(new PropertySimple(PropertyName.FILTERS, filters_json));
            }
        }

        return properties;

    }

    private void processingSizeAndColorsValuesIds(FilterView f, List<Long> ids, List<ListValue> values) {

        if (!f.getSelectedValues().isEmpty()) {
            for (Long value : f.getSelectedValues()) {
                if (ids.contains(value))
                    continue;

                ids.add(value);
                if (!values.isEmpty())
                    continue;

                values.addAll(f.getValues());
            }
        }

    }

    private String processingSizeAndColorsValuesNames(List<Long> ids, List<ListValue> values) {

        String names = null;

        if (!ids.isEmpty() && !values.isEmpty()) {
            names = "";
            String comma = "";
            for (ListValue v : values) {
                if (!ids.contains(v.getId()))
                    continue;

                names += comma + v.getValue();
                comma = ",";
            }
        }

        return names;

    }

    private String getAliasForPrice(long section_id) {

        Items section = _io.getItemByIdAndType( section_id, TypesEnum.SECTION,
                new ExtendContext(Items.class).names(PropertyName.PRICE_FILTER_ALIAS));

        if (Is.Empty(section)) {
            return "";
        }

        return section.getValue(PropertyName.PRICE_FILTER_ALIAS, String.class);
    }
    // </editor-fold>
 }
